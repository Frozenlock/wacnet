(ns wacnet.stateful
  (:require
   [reagent.core :as r]))

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
