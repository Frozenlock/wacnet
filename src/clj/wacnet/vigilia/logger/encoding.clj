(ns wacnet.vigilia.logger.encoding
  (:require [bacure.core :as bac]
            [bacure.coerce :as coerce]
            [bacure.remote-device :as rd]
            [clj-time.core :as time] ;already required in bacure
            [clojure.string :as s]
            [clojure.walk :as w]))

(defn timestamp []
  (.getMillis (time/now)))


;; ================================================================
;; ======================= Data encoding ==========================
;; ================================================================

(comment 
  ;; Here is how the data should be encoded

  ;; Firstly, as opposed to the BACnet object scheme, we will not hide
  ;; the device-id in an object property. Instead, it will be the key
  ;; by which we store the data.
{:some-device-id ;<------ device-id as the top key
 {:update "iso-string"
  :name "device-name" ;; so we don't have to dig in the properties to get it.
  :objects "<DATA>"}}

;; objects are then classified by their object-integer to make it
;; easier to sort and find them in databases.
;; --->
{:0 ;; object type 'analog-input'
 {:1 ;; object instance
  ;; then we take some of the properties
  {:Description "some description"
   :Out-of-service false
   :Present-value 13.648
   :Object-name "Some name"
   :Units "units in a string format"}}}
;; One must also notice that the properties keys are capitalized. This
;; is required to be compatible with the logger V1.
)


;; We really don't need to keep every properties. However, those we
;; choose to keep are determined by the object type.

(defn prop-by-object-type
  "Properties to retrieve based on the object type."
  [object-type]
  (let [normal-IO-prop [:object-name :description :present-value
                        :units :status-flags]]
    (when-not (re-find #"vendor-specific-(\d+)" (name object-type)) ;don't log vendor-specific
      (get {:analog-input normal-IO-prop
            :analog-ouput normal-IO-prop
            :binary-input normal-IO-prop
            :binary-output normal-IO-prop
            :device [:object-name :description :device-type
                     :vendor-identifier :vendor-name :model-name]
            :file [:object-name :description]
            :loop [:object-name :description :present-value
                   :manipulated-variable-reference
                   :controlled-variable-reference :controlled-variable-value
                   :setpoint-reference :setpoint :status-flags]
            :analog-value [:object-name :description :present-value]}
           object-type
           [:object-name :description :present-value :status-flags]))))

(defn convert-units
  "Convert units keyword to their string value."[m]
  (if-let [unit (find m :units)]
    (-> (coerce/c-engineering-units (val unit))
        ((fn [x] (assoc m :units (.toString x)))))
    m))

(defn capitalize-keys
  "Recursively transforms all map keys to their capitalized version."
  [m]
  (let [f (fn [[k v]] (if (keyword? k)
                        [(-> (name k) s/capitalize keyword) v]
                        [k v]))]
    ;; only apply to maps
    (w/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m)))

(defn encode-properties
  "Take bacure.core properties and convert them to the expected logger
  format." [m]
  (-> m convert-units capitalize-keys))


;; ================================================================
;; ======================= Data retrieval =========================
;; ================================================================


(defn get-properties-by-type
  "Get the properties and return them in the correct format.

   [{:<object-instance> properties-list}
    {:<object-instance> properties-list}...]"
  [device-id object-type object-identifier]
  (let [f (fn [m] [(keyword (-> m :object-identifier last str));; object instance
                   (-> m (dissoc :object-identifier) encode-properties)])]
    (into {}
          (map f
               (bac/remote-object-properties device-id object-identifier
                                             (prop-by-object-type object-type))))))

(defn get-properties
  "Retrieve properties for each object.

   The different properties to retrieve are determined by the object
   type. Consequently, each request is divided by
   object-type.

   The returned properties are classed in a map, associated with the
   device instance as a key. It turns associated to the object type as
   a key.

   {:0                  --- the object type
     {:1                --- the object instance
       {:Description    --- properties"
  [device-id object-identifiers]
  (let [grouped (group-by first object-identifiers)] ;; group by object type
    (into {}
          (mapcat (fn [[k v]]
                    (try {(keyword (str (.intValue (bacure.coerce/c-object-type k))))
                          (get-properties-by-type device-id k v)}
                         (catch Exception e))) grouped))))
;; Catch exceptions everywhere we can: the logging MUST NOT stop


(defn scan-device
  "Return a map with the device-id as the key.

  {:some-device-id ;<------ device-id as the top key
   {:update \"iso-string\"
    :name \"device-name\" ;; so we don't have to dig in the properties to get it.
    :scan-duration <time in ms>
    :objects \"<DATA>\"}}"
  [device-id]
  (try
    (binding [bacure.coerce/*drop-ambiguous* true]
      (let [start-time (timestamp)
            object-identifiers (bac/remote-objects device-id)
            properties (get-properties device-id object-identifiers)]
        {(keyword (str device-id))
         {:update (str (time/now))
          :name (get-in properties [:8 (keyword (str device-id)) :Object-name])
          :objects properties
          :scan-duration (- (timestamp) start-time)}}))
    (catch Exception e)))
