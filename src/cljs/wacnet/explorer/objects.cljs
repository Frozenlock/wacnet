(ns wacnet.explorer.objects
  (:require [reagent.core :as r]
            [re-com.core :as re]
            [goog.string :as gstring]
            [ajax.core :refer [GET POST DELETE]]
            [clojure.string :as s]
            [wacnet.templates.common :as tp]
            [wacnet.routes :as routes]
            [wacnet.bacnet-utils :as bu :refer-macros [object-types-map engineering-units-map]]))


(def object-int-name-map 
  (into {} (for [[k v] bu/object-types]
             [(str v) (-> (name k) 
                          (s/replace #"-" " ")
                          (s/capitalize))])))



(defn object-type-name
  "Given an object type (number), return its name."
  [object-type]
  (get object-int-name-map (str object-type)))

;; (defn short-id-to-identifier 
;;   "Convert a short id to the extended identifier. 
;;   Example: \"1.1\" --> [:analog-input 1]"
;;   [id]
;;   (let [[type instance] (s/split id #"\.")
;;         types (into {} (for [[k v] object-types]
;;                          [v k]))]
;;     [(get types (js/parseInt type)) 
;;      (js/parseInt instance)]))

(defn identifier-to-short-id [identifier]
  (let [[type instance] identifier]
    (str (get bu/object-types type) "." instance)))

(defn object-icon
  "Return an icon component depending on the object type."
  [object-type]
  (let [objects {"0"  [:span [:i.fa.fa-fw.fa-sign-in]" "[:i.fa.fa-fw.fa-signal]] ;; analog input
                 "1"  [:span [:i.fa.fa-fw.fa-signal]" "[:i.fa.fa-fw.fa-sign-out]] ;; analog output
                 "2"  [:span [:i.fa.fa-fw.fa-signal]] ;; analog value
                 "3"  [:span [:i.fa.fa-fw.fa-sign-in]" "[:i.fa.fa-fw.fa-adjust]] ;; binary input
                 "4"  [:span [:i.fa.fa-fw.fa-adjust]" "[:i.fa.fa-fw.fa-sign-out]] ;; binary output
                 "5"  [:span [:i.fa.fa-fw.fa-adjust]] ;; binary value
                 "8"  [:span [:i.fa.fa-fw.fa-server]] ;; device
                 "10" [:span [:i.fa.fa-fw.fa-file-o]] ;;file
                 "12" [:span [:i.fa.fa-fw.fa-circle-o-notch]] ;; loop
                 }]
    (get objects object-type)))

(defn object-value
  "If the object is binary, return ON/OFF instead of 0 and 1."
  [object-type present-value]
  (if (some #{object-type} ["3" "4" "5"])
    ;; binary objects
    (cond 
      (= 1 present-value) [:div.label.label-success  "ON"]
      (= 0 present-value) [:div.label.label-danger  "OFF"]
      :else [:div])
    ;; other objects
    [:span {:style {:text-align "right"}}
     (if-let [v present-value]
       (if (number? v)
         (gstring/format "%.2f" v)
         (name v))
       "---")]))



(defn api-path [api-root & args]
  (str api-root (s/join "/" args)))


(defn create-object! [loading? error? device-id props-map configs callback]
  (reset! loading? true)
  (reset! error? nil)
  (POST (api-path (:api-root configs) "bacnet" "devices" device-id "objects")
      {:handler (fn [resp]
                  (reset! loading? nil)
                  (when callback
                    (callback resp))
                  (prn resp))
       :params props-map
       :response-format :transit
       :error-handler (fn [error-resp]
                        (reset! loading? nil)
                        (reset! error? (:response error-resp)))}))


(comment
  {:object-instance "1", :object-id "0.1", :status-flags {:in-alarm false, :fault false, :out-of-service true, :overridden false}, :present-value 0, :object-type "0", :out-of-service true, :event-state :normal, :object-name "Analog Input 1", :units "no units", :href "http://localhost:3449/api/v1/bacnet/devices/1338/objects/0.1"})



(defn create-new-object-modal [device-id configs callback close-modal!]
  (let [object-props (r/atom {:object-type "0"
                              :object-name "Input"
                              :description ""
                              :units ""
                              })
        obj-type-a (r/cursor object-props [:object-type])
        units-a (r/cursor object-props [:units])
        error? (r/atom nil)
        loading? (r/atom nil)
        obj-choices (->> (for [[k v] object-int-name-map]
                           {:id k :label v})
                         (sort-by :label))
        units-choices (->> (for [[k v] bu/engineering-units]
                             {:id k :label v})
                           (sort-by :label))]
    (fn [device-id configs callback]
      [:div
       [:div.modal-header [:h3 [:i.fa.fa-plus] " New BACnet Object"]]
       [:div.modal-body 
        [re/v-box
         :gap "10px"
         :children [[re/single-dropdown :choices obj-choices
                     :model obj-type-a :on-change #(reset! obj-type-a %) :width "200px"]
                    [:div [:b "Object name"] [tp/live-edit :input object-props :object-name]]
                    [:div [:b "Description (optional)"] [tp/live-edit :textarea object-props :description]]
                    [:div [:b "Units "] 
                     [re/single-dropdown
                      :choices units-choices
                      :model units-a :on-change #(reset! units-a %) :width "300px"]]
                    (when-let [err @error?]
                    [:span.alert.alert-warning {:style {:width "100%"}} (str err)])]]]
       [:div.modal-footer
        [re/h-box :children
                   ;:gap "10px"
                   [[re/gap :size "1"]
                    [:button.btn.btn-default {:on-click close-modal!} "Cancel"]
                    [re/gap :size "10px"]
                    [:button.btn.btn-primary 
                     {:disabled (empty? (:object-name @object-props))
                      :on-click #(create-object! loading? error? device-id @object-props
                                                 configs callback)}
                     "Create!" (when @loading?
                                 [:span " " [:i.fa.fa-spinner.fa-pulse]])]]]]])))



(defn delete-object! [loading? error? device-id props-map configs callback]
  (reset! loading? true)
  (reset! error? nil)
  (DELETE (api-path (:api-root configs) "bacnet" "devices" device-id "objects"
                    (str (:object-type props-map) "." (:object-instance props-map)))
      {:handler (fn [resp]
                  (reset! loading? nil)
                  (when callback
                    (callback resp))
                  (prn resp))
       :params props-map
       :response-format :transit
       :error-handler (fn [error-resp]
                        (reset! loading? nil)
                        (reset! error? (:response error-resp)))}))


(defn delete-object-modal [bacnet-object configs callback close-modal!]
  (let [error? (r/atom nil)
        loading? (r/atom nil)]
    (fn [bacnet-object configs callback]
      [:div
       [:div.modal-header [:h3 [:i.fa.fa-trash] " Delete BACnet object"]]
       [:div.modal-body 
        [:div.alert.alert-warning "Are you sure you want to delete this object?"
         [:div [:strong (:object-name bacnet-object)]]]
        (when-let [err @error?]
                    [:span.alert.alert-warning {:style {:width "100%"}} (str err)])]
       [:div.modal-footer
        [re/h-box :children
                   [[re/gap :size "1"]
                    [:button.btn.btn-default {:on-click close-modal!} "Cancel"]
                    [re/gap :size "10px"]
                    [:button.btn.btn-danger 
                     {:on-click #(delete-object! loading? error? (:device-id bacnet-object)
                                                 bacnet-object configs callback)} "Delete!"
                     (when @loading?
                                 [:span " " [:i.fa.fa-spinner.fa-pulse]])]]]
        ]])))



;; (defn control-loop-view [obj]
;;   (let [p-id (:project-id obj)
;;         d-id (:device-id obj)
;;         o-fn (fn [k]
;;                (let [c-o (make-loop-obj obj k)]
;;                  [:span 
;;                   [inline-object c-o]
;;                   ]))]
;;     [re/v-box
;;      :children [[re/line :size "1px"]
;;                 [re/box :child [:span (bfo/object-icon (:object-type obj)) " " 
;;                                 (t/t @t/locale :objects/control-loop)]]
;;                 [re/h-box
;;                  :gap "20px"
;;                  :children
;;                  [[re/v-box
;;                    :size "1"
;;                    :children [[re/title :level :level3 :underline? true 
;;                                :label (t/t @t/locale :vigilia/controlled)]
;;                               (o-fn :controlled-variable-reference)]]
;;                   [re/line :size "1px"]
;;                   ;[re/label :label [:i.fa.fa-arrow-right]]
;;                   [re/v-box
;;                    :size "1"
;;                    :children [[re/title :level :level3 :underline? true 
;;                                :label (t/t @t/locale :vigilia/manipulated)]
;;                               (o-fn :manipulated-variable-reference)]]
;;                   [re/line :size "1px"]
;;                   [re/v-box 
;;                    :size "1"
;;                    :children [[re/title :level :level3 :underline? true 
;;                                :label (t/t @t/locale :vigilia/setpoint)]
;;                               (o-fn :setpoint-reference)]]]]]]))


(defn device-link [obj]
  (let [d-id (:device-id obj)]
    (when d-id
      [:a {:href (routes/path-for :devices-with-id :device-id d-id)}
       "("d-id")" "  " [:b (:device-name obj)]])))

(defn make-parent-device-link
  "Given the device-id, will retrieve the device name and make a link."
  [object configs]
  (let [parent-device (r/atom nil)]
    (fn [object configs]
      (let [{:keys [device-id]} object]
        (when-not (= (:device-id @parent-device) device-id)
          (GET (api-path (:api-root configs)
                         "bacnet" "devices" device-id)
              {:response-format :transit
               :handler #(reset! parent-device %)
               :error-handler prn}))
        [:div (device-link @parent-device)]))))



(defn map-to-bootstrap [map]
  [:div 
   (for [m map]
     ^{:key (key m)}
     [:div.row {:style {:background "rgba(0,0,0,0.05)"
                        :border-radius "3px"
                        :margin "2px"}}
      [:div.col-sm-4 [:strong (name (key m))]]
      [:div.col-sm-8 {:style {:overflow :hidden}}
       (let [v (val m)]
         (if (map? v)
           (map-to-bootstrap v)
           [:div {:style {:margin-left "1em"}}
            (cond (keyword? v) (name v)
                  (and (coll? v) (> (count v) 2)) [:ul
                                                   (for [[id item] (map-indexed vector v)]
                                                     ^{:key id}
                                                     [:li (str item)])]
                  :else (str v))]))]])])


(defn prop-table [object configs]
  (let [last-update-object (r/atom nil)
        error? (r/atom nil)
        loading? (r/atom nil)]
    (fn [object configs]
      (let [current-object (or @last-update-object object)
            ;; use the latest object data, or fallback to the provided object
            {:keys [project-id device-id object-type object-instance]} current-object
            object-type-name (-> object-type object-type-name)
            load-props-fn (fn []
                            (reset! loading? true)
                            (reset! error? nil)
                            (GET (:href object)
                                {:response-format :transit
                                 :handler #(do (reset! last-update-object %)
                                               (reset! loading? nil))
                                 :error-handler (fn [error-resp]
                                                  (reset! loading? nil)
                                                  (reset! error? (:response error-resp)))}))]
        [:div
         [re/v-box
          :gap "10px"
          ;:min-width "500px"
          :children [[:a {:href (:href object)
                          :target "_blank"} "See in API " [:i.fa.fa-external-link]]
                     [re/gap :size "10px"]
                     (when-let [err @error?]
                       [:span.alert.alert-warning {:style {:width "100%"}} (str err)])
                     [map-to-bootstrap (dissoc current-object :href :object-id)]
                     [:button.btn.btn-sm.btn-default {:on-click load-props-fn}
                      "Load all properties"
                      (when @loading?
                        [:span " " [:i.fa.fa-spinner.fa-pulse]])]]]]))))

(defn prop-modal [vigilia-object configs ok-btn]
  [:div
   [:div.modal-header
    [:h3 [object-icon (:object-type vigilia-object)] 
     " "(or (seq (:object-name vigilia-object))
            "< no name >")]]
   [:div.modal-body
    [prop-table vigilia-object configs]]
   [:div.modal-footer ok-btn]])
