(ns wacnet.explorer
 (:require [reagent.core :as r]
           [goog.dom :as dom]
           [hvacio-ui.controllers :as ctrls]
           [hvacio-ui.templates.modals :as modal]
           [wacnet.translation2 :as t2]
           [ajax.core :refer [GET POST]]))

(defn chart-btn [obj]
  (when (:value obj)
    [:button.btn.btn-default.btn-sm
      {:on-click #(modal/modal
                   [:div {:style { :width "100%"}}
                    [:div.modal-header
                     [:button.close {:type "button" :data-dismiss modal/modal-id}
                      [:span.glyphicon.glyphicon-remove {:aria-hidden "true"}]
                      [:span.sr-only "Close"]]
                     [:h2.modal-title "Vigilia" " " [:i.fa.fa-bar-chart-o]]]
                    [:div.modal-body
                     [:h3.text-center (t2/t @t2/locale :explorer/not-sub-vigilia)]
                     [:div.row
                      [:div.col-sm-6
                       [:img {:src "/img/graphs-views.png"
                              :style {:max-height "100%" :max-width "100%"}}]]
                      [:div.col-sm-6.text-center {:style {:margin-top "5em"}}
                       [:p (t2/t @t2/locale :explorer/guess)]
                       [:p (t2/t @t2/locale :explorer/record)]
                       [:a.btn.btn-primary.btn-lg 
                        {:href "https://hvac.io"
                         :target "_blank"
                         :style {:margin-top "2em"}}
                        (t2/t @t2/locale :explorer/learn-more)]]]]
                    [:div.modal-footer ]])}
       [:i.fa.fa-bar-chart-o]]))

;; (defn chart-btn [obj]
;;   (when (:value obj)
;;     [:button.btn.btn-default.btn-sm
;;       {:on-click #(modal/modal
;;                    [:div 
;;                     [:div.modal-header [:h2.modal-title "Vigilia" " " [:i.fa.fa-bar-chart-o]]]
;;                     [:div.modal-body
;;                      [:iframe {:height "500px" :width "100%" :style {:border "none"}
;;                                :src "https://hvac.io/vigilia/embed/g/5371147be4b0222b740851a2?tab=%3Atimeseries&bc%5B%5D=%3Aa10122..0.7..0.2..4.1"}]
;;                      ]])}
;;        [:i.fa.fa-bar-chart-o]]))


(defn show-properties [properties]
  (let [prop @properties]
    (for [[k v] prop]
      ^{:key k}
      [:div [:strong (name k)] " " (str v)])))

(enable-console-print!)

(defn details-btn [obj]
  (let [properties (atom {:<loading> "loading"})]
    [:button.btn.btn-default.btn-sm
     {:on-click (fn [e]
                  (do 
                    (GET (ctrls/api-path "/api/v1/" "object-all-properties" 
                                         (:project-id obj)
                                         (:device-id obj)
                                         (:object-id obj))
                        {:handler #(do (prn %) (reset! properties %))
                         :error-handler prn})
                  (modal/modal
                   [:div
                    [:div.modal-header
                     [:h2 "Details"]]
                    [:div.modal-body
                     [show-properties properties]]])))}
     [:i.fa.fa-list]]))

(defn trend-log-btn [obj]
  (when (= (:object-type obj) "20")
    (let [filename (or (:name obj) (str "trend-log-" (:object-instance obj)))]
         [:a.btn.btn-default.btn-sm
          {:href (str "/api/v1/trend-log/fake-project/"(:device-id obj)"/"(:object-instance obj)"/"
                      filename ".csv")}
          [:i.fa.fa-download]])))

(defn device-table-btns [obj]
  [:div {:style {:white-space "nowrap"}}
   (chart-btn obj)
   (details-btn obj)
   (trend-log-btn obj)])

(defn explorer []
  [ctrls/controllers-view "<not-provided>" {:device-table-btns device-table-btns}])
