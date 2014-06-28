(ns wacnet.client
  (:require [reagent.core :as r]
            [goog.dom :as dom]
            [hvacio-ui.controllers :as ctrls]
            [hvacio-ui.templates.modals :as modal]
            [wacnet.explorer :as exp]))


(defn by-id [id]
  (dom/getElement id))

(defn render []
  (r/render-component
   [:div.container-fluid
    [modal/modal-window]
    [exp/explorer]]
   (by-id "explorer-app")))

(defn ^:export run []
  (aset js/window "onload" render))
