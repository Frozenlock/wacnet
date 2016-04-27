(ns wacnet.core
    (:require [reagent.core :as r]
              [wacnet.explorer.devices :as d]
              [wacnet.local-device.configs :as cf]
              [wacnet.handler :as h]
              ))


(defn main-page []
  [h/main-page])

;; -------------------------
;; Initialize app

(defn mount-root []
  (r/render [main-page] (.getElementById js/document "app")))


(defn ^:export init!
  ""
  [] 
  (h/hook-browser-navigation!) ;; will redirect to default tab
  (mount-root))

