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
                                         (rs/field [s/Keyword]
                                                   {:description "Tries to find new devices on the network."})}}
                    :description "The list of all known devices."
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
        (update-in [:units] #(when % (-> (name %)
                                         (string/replace #"-" " "))))
        (assoc :object-id obj-id
               :object-instance (str (last object-identifier))
               :object-type (->> (first object-identifier)
                                 (c/key-or-num-to-int ce/object-type-map )
                                 (str)))
        binary-to-int)))

(defn object-list
  "Return a collection of object maps. Each one has, in addition to the
  given properties, the :object-id, :object-type
  and :object-instance.
  If no properties are given, only retreive the name."
  ([local-device-id device-id] (object-list local-device-id device-id nil))
  ([local-device-id device-id desired-properties]
   (let [get-prop-fn (fn [obj-id props]
                       (b/remote-object-properties 
                        local-device-id device-id obj-id props))
         object-identifiers (-> (get-prop-fn [:device device-id] :object-list)
                                first
                                :object-list)]
     (when object-identifiers 
       (for [raw-obj-map (get-prop-fn object-identifiers 
                                      (or desired-properties :object-name))]
         (-> raw-obj-map
             (assoc :device-id (str device-id))
             (prepare-obj-map)))))))


(def objects
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get {:parameters {:path {:device-id Long}
                                 :query {(s/optional-key :properties)
                                         (rs/field [s/Keyword]
                                                   {:description "List of wanted properties."})}}
                    :summary "Objects list, optionally with all their properties."
                    :description (str "List of all known objects for a given device.")
                    :swagger/tags ["BACnet"]
                    :response (fn [ctx]
                                (let [device-id (some-> ctx :parameters :path :device-id)
                                      properties (some-> ctx :parameters :query :properties)]
                                  (with-bacnet-device ctx nil
                                    (let [o-l (object-list nil device-id properties)]
                                      {:href (make-link ctx)
                                       :objects (for [o o-l]
                                                  (-> (assoc o :href (make-link ctx (str (:uri (:request ctx)) "/" (:object-id o))))
                                                      (dissoc :object-type :object-instance)))}))))}}}))



(def object
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get {:parameters {:path {:device-id Long :object-id String}
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
                                    (-> (b/remote-object-properties nil device-id (obj-id-to-object-identifier obj-id) 
                                                                    (or properties :all))
                                        (first)
                                        (prepare-obj-map)
                                        (assoc :href (make-link ctx))))))}}}))
