(ns wacnet.client
  (:require [reagent.core :as r]
            [goog.dom :as dom]
            [wacnet.explorer :as exp]))

(defn by-id [id]
  (dom/getElement id))

(defn render []
  (r/render-component
   [:div
    [exp/explorer]]
   (by-id "explorer-app")))


(defn ^:export run []
  (set! (.-onload js/window) render))
