(ns wacnet.api.bacnet.devices
  (:require [bacure.core :as b]
            [bacure.coerce :as c]
            [bacure.coerce.type.enumerated :as ce]
            [bacure.remote-device :as rd]
            [bacure.local-device :as ld]
            [yada.resource :refer [resource]]
            [bidi.bidi :refer [path-for]]
            [clojure.walk :as w]
            [schema.core :as s]
            [ring.swagger.schema :as rs]
            [yada.yada :as yada]
            [clojure.string :as string]
            [clojure.set :as cs]))


(def produced-types
  #{"application/transit+json"
    ;"application/transit+msgpack"
    "application/json" 
    "application/edn" 
    "text/html"})


(def consumed-types
  #{"application/transit+json"
    ;"application/transit+msgpack"
    "application/json" 
    "application/edn"})


(defn make-link
  "Given a request context, return the full URL (with scheme and host)
  for the current request, or for a given path."
  ([ctx] (make-link ctx (:uri (:request ctx))))
  ([ctx path]
   (str (name (get-in ctx [:request :scheme]))
        "://"
        (or (get-in ctx [:request :headers "host"]) ;; for tests
            (get-in ctx [:request :server-name]))
        path)))

(defmacro with-bacnet-device
  "Tries to execute body only if the bacnet device is found. If not,
  return an HTTP error with a short description."
  [ctx local-device-id & body]
  `(let [ldo# (ld/local-device-object ~local-device-id)]
     (cond 
       (and ldo# (.isInitialized ldo#)) 
       (do ~@body)
       
       ldo# (let [response# (:response ~ctx)
                  content-type# (yada/content-type ~ctx)]
              (merge response# {:status 500
                                :body {:error "BACnet local device not initialized."}}))
       
       :else (let [response# (:response ~ctx)]
               (merge response# {:status 500
                                 :body {:error "BACnet local device not found."}})))))


(def devices-list
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
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
                                (with-bacnet-device ctx nil
                                  (when (get-in ctx [:parameters :query :refresh])
                                    (rd/discover-network))
                                  {:href (make-link ctx)
                                   :devices (for [[k v] (rd/remote-devices-and-names nil)]
                                              {:device-id (str k)
                                               :device-name v
                                               :href (make-link ctx (str (:uri (:request ctx)) "/" k))})}))}}}))


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
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get {:summary "Device info"
                    :parameters {:path {:device-id Long}}
                    :description "A few properties and values for the given device-id."
                    :swagger/tags ["BACnet"]
                    :response (fn [ctx]
                                (let [device-id (some-> ctx :parameters :path :device-id)]
                                  (with-bacnet-device ctx nil
                                    (assoc (device-summary nil device-id)
                                           :href (make-link ctx)
                                           :objects {:href (make-link ctx (str (:uri (:request ctx)) "/objects"))}))))}}}))


(defn binary-to-int 
  "Walk the object properties and replace :inactive and :active values
  by 0 and 1. This makes binary objects similar to analog and
  multi-state objects."
  [obj-map]
  (if (some #{(str (:object-type obj-map))} 
            ["3" "4" "5"])
    (into {} (for [[k v] obj-map]
               [k (cond 
                    (= v :inactive) 0
                    (= v :active) 1
                    :else v)]))    
    obj-map))

(defn object-identifier-to-obj-id 
  "Convert a bacure object identifier to a shorter (and language
  agnostic) version.
  
  [:analog-input 1] --> \"0.1\""
  [object-identifier]
  (let [[obj-type obj-inst] object-identifier]
    (str (c/key-or-num-to-int ce/object-type-map obj-type)
         "."
         obj-inst)))

(defn obj-id-to-object-identifier [obj-id]
  (map #(Integer/parseInt %) 
       (string/split obj-id #"\.")))

(defn prepare-obj-map [obj-map]
  (let [object-identifier (:object-identifier obj-map)
        [obj-type obj-inst] object-identifier
        obj-id (object-identifier-to-obj-id object-identifier)]
    (-> (dissoc obj-map :object-identifier)
        (update-in [:units] #(when (keyword? %) 
                               (-> (name %)
                                   (string/replace #"-" " "))))
        ((fn [o] (->> (for [[k v] o]
                         (when v [k v]))
                       (remove nil?)
                       (into {}))))
        (assoc :object-id obj-id
               :object-instance (str (last object-identifier))
               :object-type (->> (first object-identifier)
                                 (c/key-or-num-to-int ce/object-type-map )
                                 (str)))
        binary-to-int)))


(defn get-object-quantity [local-device-id device-id]
  (-> (b/remote-object-properties 
       local-device-id device-id [:device device-id] [[:object-list 0]])
      first
      :object-list))

(defn get-object-list [local-device-id device-id]
  (-> (b/remote-object-properties 
       local-device-id device-id [:device device-id] :object-list)
      first
      :object-list))


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
                       (prepare-obj-map)))
        :limit limit
        :next-page (when remaining? (inc page))
        :current-page page
        :previous-page (when (> page 1) (dec page))}))))


;; (defn object-list
;;   "Return a collection of object maps. Each one has, in addition to the
;;   given properties, the :object-id, :object-type
;;   and :object-instance.
;;   If no properties are given, only retrieve the name."
;;   ([local-device-id device-id] (object-list local-device-id device-id nil))
;;   ([local-device-id device-id desired-properties]
;;    (let [get-prop-fn (fn [obj-id props]
;;                        (b/remote-object-properties 
;;                         local-device-id device-id obj-id props))
;;          object-identifiers (-> (get-prop-fn [:device device-id] :object-list)
;;                                 first
;;                                 :object-list)]
;;      (when object-identifiers 
;;        (for [raw-obj-map (get-prop-fn object-identifiers 
;;                                       (or desired-properties :object-name))]
;;          (-> raw-obj-map
;;              (assoc :device-id (str device-id))
;;              (prepare-obj-map)))))))


(defn get-object-properties
  "Get and prepare the object properties."
  [device-id obj-id properties]
  (-> (b/remote-object-properties nil device-id (obj-id-to-object-identifier obj-id) 
                                  (or properties :all))
      (first)
      (prepare-obj-map)
      (assoc :device-id (str device-id))))

(s/defschema ObjectIdentifier
  [(s/one s/Keyword "Object type")
   (s/one s/Int "Object Instance")])


(s/defschema BACnetObject
  {(s/optional-key :object-identifier) ObjectIdentifier
   (s/optional-key :object-instance) s/Str
   (s/optional-key :object-id) s/Str
   (s/optional-key :object-type) s/Str
   (s/optional-key :object-name) s/Str
   (s/optional-key :description) s/Str
   s/Any s/Any})


(defn keyword-or-int [string]
  (when-not (empty? string)
    (or (try (Integer/parseInt string)
             (catch Exception e))
        (keyword string))))

(defn clean-bacnet-object [bo]
  (let [obj (-> bo
                (update-in [:object-type] keyword-or-int)
                (update-in [:units] keyword-or-int))]
    (->> (for [[k v] obj]
           (when v [k v]))
         (remove nil?)
         (into {}))))

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

(defn make-page-link [ctx page-num limit]
  (def bbb ctx)
  (when page-num
    (let [params (:parameters ctx)
          query (:query params)]
      (make-link ctx (str (:uri (:request ctx)) 
                          "?"
                          (string/join "&"
                                       (for [[k v] (assoc query :limit limit :page page-num)]
                                         (if (coll? v)
                                           (string/join "&"
                                                        (for [i v]
                                                          (str (name k) "=" 
                                                               (name i))))
                                           (str (name k) "="v)))))))))


(def objects
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
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
                            :body BACnetObject}
               :response (fn [ctx]
                           (let [body (get-in ctx [:parameters :body])
                                 d-id (get-in ctx [:parameters :path :device-id])]
                             (when body
                               (with-bacnet-device ctx nil
                                 (let [clean-body (clean-bacnet-object body)
                                       o-id (:object-id clean-body)
                                       object-identifier (or (:object-identifier clean-body)
                                                             (when o-id (obj-id-to-object-identifier o-id)))
                                       obj-map (-> clean-body
                                                   (dissoc :object-id)
                                                   (assoc :object-identifier object-identifier))
                                       result (rd/create-remote-object! nil d-id obj-map)]
                                   (if-let [success (:success result)]
                                     (let [o-identifier (get success :object-identifier)
                                           new-o-id (object-identifier-to-obj-id o-identifier)]
                                       (-> (get-object-properties d-id new-o-id nil)
                                           (assoc :href (make-link ctx (str (:uri (:request ctx)) 
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
                    :description (str "List of all known objects for a given device.")
                    :swagger/tags ["BACnet"]
                    :response (fn [ctx]
                                (let [device-id (some-> ctx :parameters :path :device-id)
                                      limit (or (some-> ctx :parameters :query :limit) 20)
                                      page (or (some-> ctx :parameters :query :page) 1)
                                      properties (some-> ctx :parameters :query :properties)]
                                  (with-bacnet-device ctx nil
                                    (let [p-o-l (paginated-object-list nil device-id properties limit page)
                                          {:keys [next-page previous-page current-page objects]} p-o-l]
                                      (merge {:device
                                              {:href (make-link ctx
                                                                (string/replace (:uri (:request ctx)) 
                                                                                "/objects" ""))}
                                              :objects (for [o objects]
                                                         (assoc o :href (make-link ctx (str (:uri (:request ctx)) 
                                                                                            "/" (:object-id o)))))
                                              :href (make-page-link ctx current-page limit)}
                                             (when-let [l (make-page-link ctx next-page limit)]
                                               {:next {:href l}})
                                             (when-let [l (make-page-link ctx previous-page limit)]
                                               {:previous {:href l}}))))))}}}))

(s/defschema PropertyValue
  {s/Keyword  ;; property identifier
   s/Any}) ;; prop value


(def object
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:put {:summary "Update object"
                    :description (str "Update object properties.\n\n The properties expected are of the form :"
                                      "\n\n"
                                      "{property-identifier1 property-value1, "
                                      "property-identifier2 property-value2}")
                    :swagger/tags ["BACnet"]
                    :parameters {:path {:device-id Long :object-id String}
                                 :body {:properties PropertyValue}}
                    :response (fn [ctx]
                                (let [device-id (get-in ctx [:parameters :path :device-id])
                                      o-id (get-in ctx [:parameters :path :object-id])
                                      properties (get-in ctx [:parameters :body :properties])]
                                  (with-bacnet-device ctx nil
                                    (let [write-access-spec {(obj-id-to-object-identifier o-id)
                                                             properties}]                                      
                                      (let [result (rd/set-remote-properties! nil device-id write-access-spec)]
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
                             (with-bacnet-device ctx nil
                               (let [result (rd/delete-remote-object! nil device-id 
                                                                      (obj-id-to-object-identifier o-id))]
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
                                  (with-bacnet-device ctx nil
                                    (-> (get-object-properties device-id obj-id properties)
                                        (assoc :href (make-link ctx))))))}}}))


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
                                  (prepare-obj-map))])
          (into {})))))

(def multi-objects
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
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
                           (with-bacnet-device ctx nil
                             (let [ids (get-in ctx [:parameters :query :global-object-ids])
                                   properties (or (get-in ctx [:parameters :query :properties]) :all)]
                               (get-properties (map decode-global-id ids) properties))))}
              }}))
