(ns wacnet.client
  (:require [reagent.core :as r]
            [goog.dom :as dom]
            [hvacio-ui.controllers :as ctrls]
            [hvacio-ui.templates.modals :as modal]
            [wacnet.explorer :as exp]))

(enable-console-print!)

(defn by-id [id]
  (dom/getElement id))

(defn render [project-id]
  (r/render-component
   [:div.container-fluid
    [modal/modal-window]
    [exp/explorer project-id]]
   (by-id "explorer-app")))

(defn ^:export run [project-id]
  (aset js/window "onload" #(render project-id)))
