(ns wacnet.templates.resize
  (:require [reagent.core :as r]
            [goog.dom :as dom]
            [wacnet.js-util :as util]
            [goog.events :as events]
            [goog.style :as style])
  (:import [goog.events EventType]))


(defn box-size [el]
  (let [s (style/getContentBoxSize el)]
    [(.-width s)(.-height s)]))

(defn element-height [id]
  (-> (dom/getElement id) 
      (box-size)
      (last)))

(defn navbar-height []
  (-> (dom/getElement "main-navbar") box-size last))

;;;;;;; resizing

(defn window-height []
  (.-innerHeight js/window))

(defn window-width []
  (.-innerWidth js/window))

(def w-height (r/atom (window-height)))

(def w-width (r/atom (window-width)))

(let [later-fn (util/debounce-factory)]
  (events/listen js/window EventType.RESIZE #(later-fn (fn [] 
                                                         (do 
                                                             (reset! w-height (window-height))
                                                             (reset! w-width (window-width)))) 100)))

;;;;;; 
