(ns wacnet.explorer
 (:require [reagent.core :as r]
           [goog.dom :as dom]
           [hvacio-ui.controllers :as ctrls]           
           [reagent-modals.modals :as modal]
           [hvacio-ui.templates.nprogress :as nprogress]
           [wacnet.translation2 :as t2]
           [clojure.string :as s]
           [ajax.core :refer [GET POST]]))

(def dummy-project-id
  "A 'fake' project-id for when a project-id isn't provided."
  "fake-project-id")

(def vigilia-server-url (atom nil))

(defn vigilia-non-active [obj]
  [:div {:style { :width "100%"}}
   [:div.modal-header
    [:button.close {:type "button" :data-dismiss "modal"}
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
   [:div.modal-footer ]])

(defn vigilia-active [obj]
  (let [{:keys [project-id device-id object-id]} obj]
    [:div 
     [:div.modal-header 
      [:h2.modal-title "Vigilia" " " [:i.fa.fa-bar-chart-o]]]
     [:div.modal-body
      [:iframe {:height "500px" :width "100%" :style {:border "none"}
                :src (str @vigilia-server-url "/embed/g/"
                          project-id "?bc%5B%5D=%3Aa"
                          device-id ".."
                          object-id)}]]]))


(defn vigilia-modal [obj]
  (modal/modal!
   (if-not (= dummy-project-id (:project-id obj))
     (vigilia-active obj)
     (vigilia-non-active obj))))

(defn chart-btn [obj]
  (when (number? (:value obj))
    [:button.btn.btn-default.btn-sm
      {:on-click #(vigilia-modal obj)}
       [:i.fa.fa-bar-chart-o]]))

(defn map-to-bootstrap [map]
  (for [m map]
    [:div.row {:style {:background "rgba(0,0,0,0.05)"
                       :border-radius "3px"
                       :margin "2px"}}
     [:div.col-sm-4 [:strong (name (key m))]]
     [:div.col-sm-8
      (let [v (val m)]
        (if (map? v)
          (map-to-bootstrap v)
          [:p {:style {:margin-left "1em"}}
           (cond (keyword? v) (name v)
                 (and (coll? v) (> (count v) 2)) [:ul
                                                  (for [i v]
                                                    [:li (str i)])]
                 :else (str v))]))]]))

(defn show-properties [properties]
  (let [prop @properties]
    [:div.container-fluid (map-to-bootstrap prop)]))

(defn properties-btn [obj]
  (let [properties (r/atom nil)]
    [:button.btn.btn-default.btn-sm
     {:on-click (fn [e]
                  (do 
                    (nprogress/start)
                    (GET (ctrls/api-path "/api/v1/" "object-all-properties" 
                                         (:project-id obj)
                                         (:device-id obj)
                                         (:object-id obj))
                        {:handler #(do (nprogress/done)
                                       (reset! properties %))
                         :error-handler prn})
                    (modal/modal!
                     [:div
                      [:div.modal-header
                       [:button.close {:type "button" :data-dismiss "modal"}
                        [:span.glyphicon.glyphicon-remove {:aria-hidden "true"}]
                        [:span.sr-only "Close"]]
                       [:h2 "Properties" " "[:small (:name obj)]]]
                      [:div.modal-body
                       [show-properties properties]]])
                    ))}
     [:i.fa.fa-list]]))

(defn trend-log-btn [obj]
  (when (= (:object-type obj) "20")
    (let [filename (or (:name obj) (str "trend-log-" (:object-instance obj)))]
         [:a.btn.btn-default.btn-sm
          {:href (str "/api/v1/trend-log/fake-project/"(:device-id obj)"/"(:object-instance obj))
           :download (str filename ".csv")}
          [:i.fa.fa-download]])))

(defn device-table-btns [obj]
  [:div {:style {:white-space "nowrap"}}
   (chart-btn obj) ;; removed iframe charts
   (properties-btn obj)
   (trend-log-btn obj)])

(defn explorer [project-id vigilia-url]
  (let [wiki-link (fn [& ns]
                    (let [ns (->> (remove nil? ns)
                                  (map s/lower-case)
                                  (map #(s/replace % #" " "_"))
                                  (map #(s/replace % #":" "_"))
                                  (map #(s/replace % #"-" "_")))]
                      (str "https://wiki.hvac.io/doku.php?id=" (s/join ":" ns))))]
    (when vigilia-url
      (reset! vigilia-server-url vigilia-url))
    [ctrls/controllers-view (or project-id dummy-project-id)
     {:device-table-btns device-table-btns
      
      :header-fn (fn [data]
                   (let [{:keys [vendor-name model-name]} data]
                     [:div.jumbotron {:style {:padding "20px"}}
                      [:h3 {:style {:margin-top 0}}
                       (:name data) " "[:small (str " (" (:device-id data) ")")]
                       ]
                      [:div "Vendor : "
                       [:a {:href (wiki-link "suppliers" vendor-name) :target "_blank"} vendor-name]
                       " "[:i.fa.fa-external-link]]
                      [:div "Model : " [:a {:href (wiki-link "suppliers" vendor-name model-name)
                                            :target "_blank"} model-name]
                       " "[:i.fa.fa-external-link]]]))
      }]))
