(ns wacnet.api.bacnet.common
  (:require [bacure.coerce :as c]
            [bacure.coerce.type.enumerated :as ce]
            [bacure.local-device :as ld]
            [clojure.string :as string]
            [schema.core :as s]
            [yada.yada :as yada]))

(def produced-types
  #{"application/transit+json"
    "application/json"
    "application/edn"
    "text/html"})

(def consumed-types
  #{"application/transit+json"
    "application/json"
    "application/edn"})

(s/defschema ObjectIdentifier
  [(s/one s/Keyword "Object type")
   (s/one s/Int "Object Instance")])

(s/defschema PropertyValue
  {s/Keyword  ;; property identifier
   s/Any}) ;; prop value

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

(defn make-page-link [ctx page-num limit]
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

(defn obj-id-to-object-identifier [obj-id]
  (->> (map #(Integer/parseInt %)
            (string/split obj-id #"\."))
       (c/clojure->bacnet :object-identifier)
       (c/bacnet->clojure)))

(defn object-identifier-to-obj-id
  "Convert a bacure object identifier to a shorter (and language
  agnostic) version.

  [:analog-input 1] --> \"0.1\""
  [object-identifier]
  (let [[obj-type obj-inst] object-identifier]
    (str (c/key-or-num-to-int ce/object-type-map obj-type)
         "."
         obj-inst)))

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

(defn paginated-object-list
  "Return a collection of object maps. Each one has, in addition to the
  given properties, the :object-id, :object-type
  and :object-instance.
  If no properties are given, only retrieve the name."
  [{:keys [device-id obj-qty-fn object-identifiers-fn get-prop-fn desired-properties limit page]
    :or {limit 20, page 0}}]
  (let [obj-qty (obj-qty-fn)
        all-array-indexes (range 1 (inc obj-qty))
        current-pos (* limit (dec page))
        remaining-positions (drop current-pos all-array-indexes)
        remaining? (> (count remaining-positions) limit)
        object-identifiers (object-identifiers-fn limit remaining-positions)]
    (when object-identifiers
      {:objects       (for [raw-obj-map (get-prop-fn object-identifiers
                                                     (or desired-properties [:object-name]))]
                        (-> raw-obj-map
                            (assoc :device-id (str device-id))
                            (prepare-obj-map)))
       :limit         limit
       :next-page     (when remaining? (inc page))
       :current-page  page
       :previous-page (when (> page 1) (dec page))})))


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

(defmacro with-save-local
  "Unless an error is thrown, save the local device state."
  [& body]
  `(let [result# (do ~@body)]
     (ld/save-local-device-backup!)
     result#))
