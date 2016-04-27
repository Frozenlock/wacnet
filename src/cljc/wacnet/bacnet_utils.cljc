(ns wacnet.bacnet-utils
  #?(:clj (:require [bacure.coerce.type.enumerated :as enum]
                    [bacure.coerce :as c]
                    [clojure.set :as cset]
                    [clojure.string :as s])))

#?(:clj (defmacro object-type-map []
          (into {} (for [[k v] enum/object-type-map]
                     [(str v) (-> (name k) 
                                  (s/replace #"-" " ")
                                  (s/capitalize))]))))
