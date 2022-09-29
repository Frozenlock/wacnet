(ns wacnet.api.bacnet.local-device
  (:require [bacure.core :as b]
            [bacure.local-save :as save]
            [bacure.remote-device :as rd]
            [bacure.local-device :as ld]
            [bacure.network :as net]
            [yada.resource :refer [resource]]
            [bidi.bidi :refer [path-for]]
            [clojure.walk :as w]
            [schema.core :as s]
            [ring.swagger.schema :as rs]
            [yada.yada :as yada]
            [wacnet.api.bacnet.common :as co]
            [clojure.set :as cs]
            [clojure.string :as string]))

(def allowed-configs-keys
  #{:broadcast-address :device-id :port
    :apdu-timeout
    :number-of-apdu-retries :description
    :object-name
    :foreign-device-target})

(defn get-current-configs
  "Get the configuration from the local-device or (if not found) the
  configuration file."
  [local-device-id]
  (select-keys (merge ld/default-configs
                      (or (ld/local-device-backup)
                          (save/get-configs)))
               allowed-configs-keys))

(defn reset-and-save!
  "Reset the local device and save the configurations."
  [configs]
  (let [current-configs (ld/local-device-backup)]
    (ld/clear-all!) ;; remove all local devices
    ;; boot the local device
    (b/boot-up! (merge current-configs configs))
    ;; create a config backup and save it locally
    (select-keys (ld/save-local-device-backup!)
                 allowed-configs-keys)))


(s/defschema ForeignDevice
  {(s/optional-key :host) s/Str (s/optional-key :port) s/Int})

(s/defschema LocalConfigs
  {(s/optional-key :broadcast-address) s/Str
   (s/optional-key :device-id) s/Int
   (s/optional-key :port) s/Int
   (s/optional-key :description) s/Str
   (s/optional-key :apdu-timeout) s/Int
   (s/optional-key :apdu-segment-timeout) s/Int
   (s/optional-key :number-of-apdu-retries) s/Int
   (s/optional-key :object-name) s/Str
   (s/optional-key :foreign-device-target) (s/maybe ForeignDevice)})


(def bacnet-configs
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :methods {:get {:summary "BACnet configs"
                    :description "BACnet configs for the Wacnet local device."
                    :swagger/tags ["Local Device"]
                    :response (fn [ctx]
                                (get-current-configs nil))}
              :post {:description (str "Update the provided fields in the local device configs. "
                                       "Will automatically reboot the local device with the new configs.")
                     :swagger/tags ["Local Device"]
                     :parameters {:body LocalConfigs}
                     :response (fn [ctx]
                                 (let [new-configs (some-> ctx :parameters :body)]
                                   (when new-configs
                                     (reset-and-save! new-configs))))}}}))

(defn local-device-summary [local-device-id]
  (let [initialized? (some-> (ld/local-device-object local-device-id) (.isInitialized))]
    (merge {:initialized initialized?
            :available-interfaces (net/interfaces-and-ips)}
           (when initialized? {:known-remote-device (count (rd/remote-devices local-device-id))}))))

(def local-device
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :methods {:get {:summary "Local device"
                    :description "BACnet local device info"
                    :swagger/tags ["Local Device"]
                    :response (fn [ctx]
                                (local-device-summary nil))}}}))

(defn get-object-list
  []
  (map :object-identifier (ld/local-objects)))

(defn get-obj-props
  "If provided `desired-properties` is nil, will return all properties."
  [oid desired-properties]
  (let [object (first (filter #(= oid (:object-identifier %)) (ld/local-objects)))
        props (if (seq desired-properties)
                (select-keys object (conj desired-properties :object-identifier))
                object)]
    (when-not (empty? props)
      props)))

(defn get-device-id []
  (last (for [o (ld/local-objects)
              :let [[obj-type obj-inst] (:object-identifier o)]
              :when (= :device obj-type)]
          obj-inst)))

(defn paginated-object-list
  [desired-properties limit page]
  (let [object-list (get-object-list)]
    (co/paginated-object-list
     {:obj-qty-fn            #(count object-list)
      :device-id             (get-device-id)
      :object-identifiers-fn (fn [limit remaining-positions]
                               (->>  object-list
                                     (drop (dec (first remaining-positions)))
                                     (take limit)))
      :get-prop-fn           (fn [object-identifiers properties]
                               (for [oid object-identifiers]
                                 (get-obj-props oid properties)))
      :desired-properties    desired-properties
      :limit                 limit
      :page                  page})))

(def objects
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:post
              {:summary      "New object"
               :description  (str "Create a new BACnet object. It is possible to give an object ID, but it is "
                                 "suggested to rather give the object-type and let the device handle "
                                 "the object-instance itself.\n\n"
                                 "The object-type can be the integer value or the keyword. "
                                 "(Ex: \"0\" or \"analog-input\" for an analog input)\n\n"
                                 "It is possible to give additional properties such as object-name.")
               :swagger/tags ["Local Device"]
               :parameters   {:body co/BACnetObject}
               :response     (fn [ctx]
                               (let [body (get-in ctx [:parameters :body])]
                                 (when body
                                   (co/with-bacnet-device ctx nil
                                     (let [clean-body (co/clean-bacnet-object body)
                                           o-id (:object-id clean-body)
                                           object-identifier (or (:object-identifier clean-body)
                                                                 (when o-id (co/obj-id-to-object-identifier o-id)))
                                           obj-map (-> clean-body
                                                       (dissoc :object-id)
                                                       (assoc :object-identifier object-identifier))
                                           result (try {:success
                                                        (co/with-save-local
                                                          (let [object (ld/add-object! nil obj-map)]
                                                            (let [obj (co/prepare-obj-map object)]
                                                              (assoc obj :href (co/make-link ctx (str (:uri (:request ctx))
                                                                                                      "/" (:object-id obj)))))))}
                                                       (catch Exception e
                                                         {:error (.getMessage e)}))]
                                       (if-let [obj (:success result)]
                                         obj
                                         (merge (:response ctx)
                                                {:status 500
                                                 :body   result})))))))}

              :get {:parameters {:query {(s/optional-key :limit)
                                         (rs/field Long
                                                   {:description "Maximum number of objects per page."})
                                         (s/optional-key :page) Long
                                         (s/optional-key :properties)
                                         (rs/field [s/Keyword]
                                                   {:description "List of wanted properties."})}}
                    :summary "Objects list, optionally with all their properties."
                    :description (str "List of all known objects."
                                      "\n\n"
                                      "Unless specified, the page will default to 1 and limit to 50 objects.")
                    :swagger/tags ["Local Device"]
                    :response (fn [ctx]
                                (let [limit (or (some-> ctx :parameters :query :limit) 50)
                                      page (or (some-> ctx :parameters :query :page) 1)
                                      properties (some-> ctx :parameters :query :properties)]
                                  (co/with-bacnet-device ctx nil
                                    (let [p-o-l (paginated-object-list properties limit page)
                                          {:keys [next-page previous-page current-page objects]} p-o-l]
                                      (merge {:device
                                              {:href (co/make-link ctx
                                                                   (string/replace (:uri (:request ctx))
                                                                                   "/objects" ""))}
                                              :objects (for [o objects]
                                                         (assoc o :href (co/make-link ctx (str (:uri (:request ctx))
                                                                                               "/" (:object-id o)))))
                                              :href    (co/make-page-link ctx current-page limit)}
                                             (when-let [l (co/make-page-link ctx next-page limit)]
                                               {:next {:href l
                                                       :page next-page}})
                                             (when-let [l (co/make-page-link ctx previous-page limit)]
                                               {:previous {:href l
                                                           :page previous-page}}))))))}}}))


(def object
  (resource
   {:produces       [{:media-type co/produced-types
                      :charset    "UTF-8"}]
    :consumes       [{:media-type co/consumed-types
                      :charset    "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods        {:put {:summary      "Update object"
                           :description  (str "Update object properties.\n\n The properties expected are of the form :"
                                              "\n"
                                              "{property-identifier1 property-value1, "
                                              "property-identifier2 property-value2}")
                           :swagger/tags ["Local Device"]
                           :parameters   {:path {:object-id String}
                                          :body {:properties co/PropertyValue}}
                           :response     (fn [ctx]
                                           (let [o-id (get-in ctx [:parameters :path :object-id])
                                                 properties (get-in ctx [:parameters :body :properties])]
                                             (co/with-bacnet-device ctx nil
                                               (let [result (try {:success
                                                                  (co/with-save-local
                                                                    (ld/update-object! nil (assoc properties :object-identifier (co/obj-id-to-object-identifier o-id)))
                                                                    "Object updated")}
                                                                 (catch Exception e
                                                                   {:error (.getMessage e)}))]
                                                 (if (:success result)
                                                   result
                                                   (merge (:response ctx)
                                                          {:status 500
                                                           :body   result}))))))}
                     :delete
                     {:summary      "Delete object"
                      :description  "Delete the given object."
                      :swagger/tags ["Local Device"]
                      :parameters   {:path {:object-id String}}
                      :response     (fn [ctx]
                                      (let [o-id (get-in ctx [:parameters :path :object-id])]
                                        (co/with-bacnet-device ctx nil
                                          (let [result (try (co/with-save-local
                                                              (ld/remove-object! nil (co/obj-id-to-object-identifier o-id))
                                                              {:success "Object deleted"})
                                                            (catch Exception e
                                                              {:error (.getMessage e)}))]
                                            (if (:success result)
                                              result
                                              (merge (:response ctx)
                                                     {:status 500
                                                      :body   result}))))))}
                     :get {:parameters   {:path  {:object-id String}
                                          :query {(s/optional-key :properties)
                                                  (rs/field [s/Keyword]
                                                            {:description "List of wanted properties."})}}
                           :summary      "Object properties."
                           :description  (str "Return the object properties specified in the query parameter. "
                                              "If none is given, try to return all of them.")
                           :swagger/tags ["Local Device"]
                           :response     (fn [ctx]
                                           (let [obj-id (some-> ctx :parameters :path :object-id)
                                                 properties (some-> ctx :parameters :query :properties)]
                                             (co/with-bacnet-device ctx nil
                                               (some-> (co/obj-id-to-object-identifier obj-id)
                                                       (get-obj-props properties)
                                                       (co/prepare-obj-map)
                                                       (assoc :href (co/make-link ctx))))))}}}))
