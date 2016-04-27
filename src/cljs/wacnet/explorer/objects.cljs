(ns wacnet.explorer.objects
  (:require [reagent.core :as r]
            [re-com.core :as re]
            [goog.string :as gstring]
            [wacnet.bacnet-utils :as bu :refer-macros [object-type-map]]))


(def object-int-name-map (object-type-map))

(defn object-type-name
  "Given an object type (number), return its name."
  [object-type]
  (get object-int-name-map (str object-type)))

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




;; (defn prop-table [object]
;;   (let [last-update-object (r/atom nil)]
;;     (fn [object]
;;       (let [current-object (or @last-update-object object)
;;             ;; use the latest object data, or fallback to the provided object
;;             {:keys [project-id device-id object-type object-instance]} current-object
;;             object-type-name (-> object-type bfo/object-type-name)
;;             object-api-url (api-str "project" project-id
;;                                     "devices" device-id
;;                                     "objects" (str object-type "." object-instance))]
            
;;         ;; try to get the very last value
;;         (when-not @last-update-object
;;           (GET (:href object)
;;               {:response-format :transit
;;                :handler #(reset! last-update-object (obj/vigilia-object %))
;;                :error-handler prn}))
;;         [re/v-box
;;          ;:size "auto"
;;          :min-width "500px"
;;          :children [[re/title :level :level2 :label (:object-name object)]
;;                     [re/line :size "3px" :color (or (:color object) "grey")]
;;                     [re/gap :size "10px"]
;;                     [:span (t/t @t/locale :vigilia/last-scan) " : "
;;                      (let [scan-time (-> (:last-update current-object)
;;                                          (js/moment.))]
;;                        [:span (.format scan-time "llll") " "
;;                         "("(.fromNow scan-time)")"])]
;;                     [re/h-box
;;                      :gap "1"
;;                      :align :center
;;                      :children [[:span (t/t @t/locale :vigilia/records)" : " (:records object)
;;                                  " ("[:a {:href (:href object)
;;                                           :target "_blank"} "API"]")"]
;;                                 [graphs/graph-btn object add-to-bf!]]]
                                         
                    
;;                     [re/gap :size "10px"]
                    
;;                     [:table.table.table-striped.table-hover
;;                      [:tbody
;;                       [prop-row (str (t/t @t/locale :briefcase/device) ": ")
;;                        [make-parent-device-link object]]
;;                       [prop-row (str (t/t @t/locale :vigilia/description)" : ") (:description object)]
;;                       [prop-row (str (t/t @t/locale :vigilia/type)" : ") 
;;                        [:span [:span {:style {:border "1px solid"}}
;;                                (bfo/object-icon object-type)] " - " object-type-name " #" object-instance]]
;;                       ;; for the current value, we try to show the very last value.
;;                       [prop-row (str (t/t @t/locale :briefcase/value) ": ") 
;;                        (bfo/object-value (or @last-update-object object))]
;;                       [prop-row (str (t/t @t/locale :briefcase/unit) ": ") (:units object)]]]
                    
;;                       (when (= "12" (:object-type object)) ;; control loop
;;                         [re/v-box
;;                          :children [[re/gap :size "20px"]
;;                                     (control-loop-view object)]])
;;                     ;[draggable-link object]
;;                     ]]))))
