(ns wacnet.stateful
  (:require [reagent.core :as r]
            [alandipert.storage-atom :as sa]))

(def show-ids-a (-> (r/atom nil)
                    (sa/local-storage :show-ids)))

(defonce state (r/atom {}))


(defn get-in-state [ks]
  (let [temp-a (r/cursor state ks)]
    @temp-a))

(defn set-in-state! [ks v]
  (swap! state assoc-in ks v)
  nil)

(defn url-params-atom []
  (r/cursor state [:url-params]))

(defn set-url-params! [m]
  (set-in-state! [:url-params] m))

(defn get-url-params []
  (get-in-state [:url-params]))
