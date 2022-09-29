(ns wacnet.api.bacnet.devices
  (:require [bacure.coerce :as c]
            [bacure.coerce.type.enumerated :as ce]
            [bacure.core :as b]
            [bacure.remote-device :as rd]
            [clojure.set :as cs]
            [clojure.string :as string]
            [ring.swagger.schema :as rs]
            [schema.core :as s]
            [wacnet.api.bacnet.common :as co]
            [yada.resource :refer [resource]]))

(def devices-list
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get {:summary "Devices list"
                    :parameters {:query {(s/optional-key :refresh)
                                         (rs/field s/Bool
                                                   {:description (str "Tries to find new devices on the network"
                                                                      " using a WhoIs broadcast.")})}}
                    :description "The list of all known devices with an optional refresh."
                    :swagger/tags ["BACnet"]
                    :response (fn [ctx]
                                (co/with-bacnet-device ctx nil
                                  (when (get-in ctx [:parameters :query :refresh])
                                    (rd/discover-network))
                                  {:href (co/make-link ctx)
                                   :devices (for [[k v] (rd/remote-devices-and-names nil)]
                                              {:device-id (str k)
                                               :device-name v
                                               :href (co/make-link ctx (str (:uri (:request ctx)) "/" k))})}))}}}))


(defn device-summary [local-device-id device-id]
  (-> (b/remote-object-properties local-device-id device-id [:device device-id]
                                  [:description :vendor-identifier 
                                   :vendor-name :object-name :model-name])
      (first)
      (dissoc :object-identifier)
      (cs/rename-keys {:object-name :device-name})
      (assoc :device-id (str device-id))))

(def device
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get {:summary "Device info"
                    :parameters {:path {:device-id Long}}
                    :description "A few properties and values for the given device-id."
                    :swagger/tags ["BACnet"]
                    :response (fn [ctx]
                                (let [device-id (some-> ctx :parameters :path :device-id)]
                                  (co/with-bacnet-device ctx nil
                                    (assoc (device-summary nil device-id)
                                           :href (co/make-link ctx)
                                           :objects {:href (co/make-link ctx (str (:uri (:request ctx)) "/objects"))}))))}}}))


(defn get-object-quantity [local-device-id device-id]
  (-> (b/remote-object-properties 
       local-device-id device-id [:device device-id] [[:object-list 0]])
      first
      :object-list))


; TODO: use co/paginated-object-list instead
(defn paginated-object-list
  "Return a collection of object maps. Each one has, in addition to the
  given properties, the :object-id, :object-type
  and :object-instance.
  If no properties are given, only retrieve the name."
  ([local-device-id device-id] (paginated-object-list local-device-id device-id nil 20 1))
  ([local-device-id device-id desired-properties limit page]
   (let [obj-qty (get-object-quantity local-device-id device-id)
         all-array-indexes (range 1 (inc obj-qty))
         cursor-pos (* limit (dec page))
         after-cursor (drop cursor-pos all-array-indexes)
         remaining? (> (count after-cursor) limit)
         desired-array (for [i (take limit after-cursor)]
                         [:object-list i])
         get-prop-fn (fn [obj-ids props]
                       (b/remote-object-properties 
                        local-device-id device-id obj-ids props))
         object-identifiers (-> (if (> obj-qty limit)
                                  (get-prop-fn [:device device-id] desired-array)
                                  (get-prop-fn [:device device-id] :object-list))
                                first
                                :object-list)]
     (when object-identifiers 
       {:objects (for [raw-obj-map (get-prop-fn object-identifiers 
                                                (or desired-properties :object-name))]
                   (-> raw-obj-map
                       (assoc :device-id (str device-id))
                       (co/prepare-obj-map)))
        :limit limit
        :next-page (when remaining? (inc page))
        :current-page page
        :previous-page (when (> page 1) (dec page))}))))

(defn get-object-properties
  "Get and prepare the object properties."
  [device-id obj-id properties]
  (-> (b/remote-object-properties nil device-id (co/obj-id-to-object-identifier obj-id) 
                                  (or properties :all))
      (first)
      (co/prepare-obj-map)
      (assoc :device-id (str device-id))))


(defn clean-errors 
  "Remove the objects that can't be transmitted by the API."
  [b-error]
  (if (map? b-error)
    (->> (for [[k v] b-error]
           [k (dissoc v 
                      :apdu-error
                      :timeout-error)])
         (into {}))
    b-error))



(def objects
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:post 
              {:summary "New object"
               :description (str "Create a new BACnet object. It is possible to give an object ID, but it is "
                                 "suggested to rather give the object-type and let the remote device handle "
                                 "the object-instance itself.\n\n"
                                 "The object-type can be the integer value or the keyword. "
                                 "(Ex: \"0\" or \"analog-input\" for an analog input)\n\n"
                                 "It is possible to give additional properties (such as object-name).")
               :swagger/tags ["BACnet"]
               :parameters {:path {:device-id Long}
                            :body co/BACnetObject}
               :response (fn [ctx]
                           (let [body (get-in ctx [:parameters :body])
                                 d-id (get-in ctx [:parameters :path :device-id])]
                             (when body
                               (co/with-bacnet-device ctx nil
                                 (let [clean-body (co/clean-bacnet-object body)
                                       o-id (:object-id clean-body)
                                       object-identifier (or (:object-identifier clean-body)
                                                             (when o-id (co/obj-id-to-object-identifier o-id)))
                                       obj-map (-> clean-body
                                                   (dissoc :object-id)
                                                   (assoc :object-identifier object-identifier))
                                       result (rd/create-remote-object! nil d-id obj-map)]
                                   (if-let [success (:success result)]
                                     (let [o-identifier (get success :object-identifier)
                                           new-o-id (co/object-identifier-to-obj-id o-identifier)]
                                       (-> (get-object-properties d-id new-o-id nil)
                                           (assoc :href (co/make-link ctx (str (:uri (:request ctx)) 
                                                                            "/" new-o-id)))))
                                     (merge (:response ctx) 
                                            {:status 500
                                             :body (clean-errors result)})))))))}
              :get {:parameters {:path {:device-id Long}
                                 :query {(s/optional-key :limit) 
                                         (rs/field Long
                                                   {:description "Maximum number of objects per page."})
                                         (s/optional-key :page) Long
                                         (s/optional-key :properties)
                                         (rs/field [s/Keyword]
                                                   {:description "List of wanted properties."})}}
                    :summary "Objects list, optionally with all their properties."
                    :description (str "List of all known objects for a given device."
                                      "\n\n"
                                      "Unless specified, the page will default to 1 and limit to 50 objects.")
                    :swagger/tags ["BACnet"]
                    :response (fn [ctx]
                                (let [device-id (some-> ctx :parameters :path :device-id)
                                      limit (or (some-> ctx :parameters :query :limit) 50)
                                      page (or (some-> ctx :parameters :query :page) 1)
                                      properties (some-> ctx :parameters :query :properties)]
                                  (co/with-bacnet-device ctx nil
                                    (let [p-o-l (paginated-object-list nil device-id properties limit page)
                                          {:keys [next-page previous-page current-page objects]} p-o-l]
                                      (merge {:device
                                              {:href (co/make-link ctx
                                                                (string/replace (:uri (:request ctx)) 
                                                                                "/objects" ""))}
                                              :objects (for [o objects]
                                                         (assoc o :href (co/make-link ctx (str (:uri (:request ctx)) 
                                                                                            "/" (:object-id o)))))
                                              :href (co/make-page-link ctx current-page limit)}
                                             (when-let [l (co/make-page-link ctx next-page limit)]
                                               {:next {:href l
                                                       :page next-page}})
                                             (when-let [l (co/make-page-link ctx previous-page limit)]
                                               {:previous {:href l
                                                           :page previous-page}}))))))}}}))


(def object
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:put {:summary "Update object"
                    :description (str "Update object properties.\n\n The properties expected are of the form :"
                                      "\n"
                                      "{property-identifier1 property-value1, "
                                      "property-identifier2 property-value2}"
                                      "\n\n"
                                      "WARNING : you can specify the priority, but you should ONLY do so"
                                      " if you understand the consequences.")
                    :swagger/tags ["BACnet"]
                    :parameters {:path {:device-id Long :object-id String}
                                 :body {:properties co/PropertyValue
                                        (s/optional-key :priority) Long}}
                    :response (fn [ctx]
                                (let [device-id (get-in ctx [:parameters :path :device-id])
                                      o-id (get-in ctx [:parameters :path :object-id])
                                      properties (get-in ctx [:parameters :body :properties])
                                      priority (get-in ctx [:parameters :body :priority] nil)]
                                  (co/with-bacnet-device ctx nil
                                    (let [write-access-spec {(co/obj-id-to-object-identifier o-id)
                                                             (for [[k v] properties]
                                                               [k (rd/advanced-property v priority nil)])}]
                                      (let [result (try (rd/set-remote-properties! nil device-id write-access-spec)
                                                        (catch Exception e
                                                          {:error (.getMessage e)}))]
                                        (if (:success result)
                                          result
                                          (merge (:response ctx) 
                                                 {:status 500
                                                  :body (clean-errors result)})))))))}
              :delete 
              {:summary "Delete object"
               :description "Delete the given object."
               :swagger/tags ["BACnet"]
               :parameters {:path {:device-id Long :object-id String}}
               :response (fn [ctx]
                           (let [device-id (get-in ctx [:parameters :path :device-id])
                                 o-id (get-in ctx [:parameters :path :object-id])]
                             (co/with-bacnet-device ctx nil
                               (let [result (rd/delete-remote-object! nil device-id 
                                                                      (co/obj-id-to-object-identifier o-id))]
                                 (if (:success result)
                                   result
                                   (merge (:response ctx) 
                                          {:status 500
                                           :body (clean-errors result)}))))))}
              :get {:parameters {:path {:device-id Long :object-id String}
                                 :query {(s/optional-key :properties)
                                         (rs/field [s/Keyword]
                                                   {:description "List of wanted properties."})}}
                    :summary "Object properties."
                    :description (str "Return the object properties specified in the query parameter. "
                                      "If none is given, try to return all of them.")
                    :swagger/tags ["BACnet"]
                    :response (fn [ctx]
                                (let [device-id (some-> ctx :parameters :path :device-id)
                                      obj-id (some-> ctx :parameters :path :object-id)
                                      properties (some-> ctx :parameters :query :properties)]
                                  (co/with-bacnet-device ctx nil
                                    (-> (get-object-properties device-id obj-id properties)
                                        (assoc :href (co/make-link ctx))))))}}}))


(defn decode-global-id
  "Return a map containing useful info from the global-ids."
  [id]
  (let [items (string/split id #"\.")
        [d-id o-type o-inst] (map #(Integer/parseInt %) (take-last 3 items))]
    {:device-id d-id
     :object-type o-type
     :object-instance o-inst
     :object-identifier (->> [o-type o-inst] 
                             (c/clojure->bacnet :object-identifier) 
                             (c/bacnet->clojure))
     :global-id id}))

(defn get-properties 
  "Get the requested properties in parallel for every devices."
  ([objects-maps] (get-properties objects-maps :all))
  ([objects-maps properties]
   ;; first get retrieve the data from the remote devices...
   (let [by-devices (group-by :device-id objects-maps)
         result-map (->> (pmap 
                          (fn [[device-id objects]]
                            [device-id (->> (for [result (b/remote-object-properties 
                                                          nil device-id 
                                                          (map :object-identifier objects) 
                                                          properties)]
                                              [(:object-identifier result) result])
                                            (into {}))]) by-devices)
                         (into {}))]
     ;; then we format it back into a map using the global ids as keys.

     (->> (for [obj objects-maps]
            [(:global-id obj) (-> (get-in result-map [(:device-id obj) (:object-identifier obj)])
                                  (co/prepare-obj-map))])
          (into {})))))

(def multi-objects
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type co/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get
              {:summary "Multi object properties"
               :description (str "Retrieve the properties of multiple objects at the same time. "
                                 "A subset of properties can be returned by providing them in the optional "
                                 "'properties' field.\n\n The 'global-object-id' is the 'object-id' prepended "
                                 "with the 'device-id'."
                                 "\n\nExample: \"my-awesome.prefix.10122.3.3\"")
               :swagger/tags ["BACnet"]
               :parameters {:query {(s/optional-key :properties)
                                    (rs/field [s/Keyword]
                                              {:description "List of wanted properties."})
                                    :global-object-ids [s/Str]}}
               :response (fn [ctx]
                           (co/with-bacnet-device ctx nil
                             (let [ids (get-in ctx [:parameters :query :global-object-ids])
                                   properties (or (get-in ctx [:parameters :query :properties]) :all)]
                               (get-properties (map decode-global-id ids) properties))))}
              }}))
