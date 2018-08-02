(ns wacnet.bacnet-utils
  (:require [clojure.set :as cset]
            [clojure.string :as s]
            #?(:clj [bacure.coerce.type.enumerated :as enum])
            #?(:clj [bacure.coerce :as c]))
  #?(:cljs (:require-macros [wacnet.bacnet-utils :refer [object-types-map engineering-units-map]])))

#?(:clj (defmacro object-types-map []
          enum/object-type-map))

(def object-types (object-types-map))

(defn short-id-to-identifier 
  "Convert a short id to the extended identifier. 
  Example: \"1.1\" --> [:analog-input 1]"
  [id]
  (let [[type instance] (s/split id #"\.")
        types (into {} (for [[k v] object-types]
                         [v k]))]
    [(let [parsed (#?(:cljs js/parseInt
                      :clj Integer/parseInt) type)]
       (get types parsed parsed))
     (#?(:cljs js/parseInt
         :clj Integer/parseInt) instance)]))

(defn identifier-to-short-id 
  "Convert an object identifier to its short string equivalent.
   Example: \"1.1\" --> [:analog-input 1] "
  [identifier]
  (let [[type instance] identifier]
    (str (get object-types type type) "." instance)))

;; #?(:clj (defmacro object-type-map-name []
;;           (into {} (for [[k v] enum/object-type-map]
;;                      [(str v) (-> (name k) 
;;                                   (s/replace #"-" " ")
;;                                   (s/capitalize))]))))

#?(:clj (defmacro engineering-units-map []
          (into {} (for [[k v] enum/engineering-units-map]
                     [(str v) (-> (name k) 
                                  (s/replace #"-" " ")
                                  (s/capitalize))]))))

(def engineering-units (engineering-units-map))
