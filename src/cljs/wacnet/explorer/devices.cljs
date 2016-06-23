(ns wacnet.explorer.devices
  (:require [reagent.core :as r]
            [cljs.reader :as reader]
            [ajax.core :refer [GET POST]]          
            [wacnet.js-util :as util]
            [wacnet.templates.common :as common]
            [wacnet.explorer.objects :as wo]
            [goog.string :as gstring]
            [wacnet.routes :as routes]
            [clojure.string :as s]
            [re-com.core :as re :refer-macros [handler-fn]]
            [goog.Timer :as timer]
            [cljsjs.fixed-data-table]
            [clojure.set :as cset]            
            [reagent-modals.modals :as mod]
            [cljs.core.async :as async :refer [<! >! chan put! onto-chan]])
  (:import [goog.events EventType])
  (:require-macros [cljs.core.async.macros :refer [go]]))







(defn api-path [api-root & args]
  (str api-root (s/join "/" args)))



;=======================================================
;================== Devices overview ===================
;=======================================================

;;; The main devices list. In this column, we show the device ID and
;;; the device name. The user can then click on a device to see its
;;; objects in details.


(defn pad-id-length [id target-length]
  (let [padding (- target-length (count id))]
    [:span 
     [:span {:style {:visibility "hidden"}} (s/join (repeat padding "0"))]
     id]))
     




(defn nav-item
  [tab selected-tab-id show-ids? ids-length local-click configs]
  (let [this-id (atom (:id tab))] ;; keep track manually through updates
    ;; -> component-did-update does not receive the new arguments
    (r/create-class
     {:component-did-mount #(when (= @selected-tab-id (:id tab))
                              (js/window.requestAnimationFrame
                               (fn [] (.scrollIntoView (r/dom-node %)))))
      :component-did-update (fn [this]
                              (let [id @this-id]
                              (when (= @selected-tab-id id)
                                (if @local-click
                                  ;; only scroll if we are coming from 'elsewhere'
                                  (reset! local-click nil)
                                  (.scrollIntoView (r/dom-node this))))))
      :reagent-render
      (fn [tab selected-tab-id show-ids? ids-length on-select-tab]
        (let [selected?   (= @selected-tab-id (:id tab))
              ids? @show-ids?]
          (reset! this-id (:id tab))
          [:div.nav-item
           {:class (when selected? "selected")
            :title (:name tab)

            :on-click (handler-fn (do (reset! local-click (:id tab))
                                      (if-not (:vigilia-mode configs)                                        
                                        (routes/goto-hash!
                                         (routes/path-for :devices-with-id :device-id (:id tab)))
                                        (reset! selected-tab-id (:id tab)))))}
            [:div.device-id
             {:style {:max-width (if-not ids? "0px" (* ids-length 12))}}
             [:strong.nav-ids 
              (pad-id-length (:id tab) ids-length)]]
           ;(when ids? [re/line :class "separator" :color nil])
           [:span (or (:name tab) "< no name >")]]))})))



(defn filter-devices [devices-list filter-string]
  (let [regexp (re-pattern (util/make-fuzzy-regex filter-string))]
    (filter #(or (re-find regexp (or (:id %) "< no name >"))
                 (re-find regexp (or (:name %) "< no name >"))) devices-list)))


(defn sort-by-id-btn [manipulated-devices-list local-click]
  (let [ascending? (r/atom true)]
    (fn []
      (let [asc? @ascending?]
        [re/button
         :tooltip          "Sort by device ID"
         :tooltip-position :below-left
         :on-click         #(do (reset! local-click true)
                                (swap! manipulated-devices-list
                                       update-in [:devices]
                                       (fn [coll]
                                         (sort-by :id
                                                  (fn [id-str1 id-str2]
                                                    ((if asc? compare (comp (fn [n] (* -1 n)) compare))
                                                     (reader/read-string id-str1)
                                                     (reader/read-string id-str2)))
                                                  coll)))
                                (swap! ascending? not))
         :label            (if asc? 
                             [:i.fa.fa-sort-numeric-asc] 
                             [:i.fa.fa-sort-numeric-desc])]))))


(defn sort-by-name-btn [manipulated-devices-list local-click]
  (let [ascending? (r/atom true)]
    (fn []
      (let [asc? @ascending?]
        [re/button
         :tooltip          "Sort by device name"
         :tooltip-position :below-left
         :on-click         #(do (reset! local-click true)
                                (swap! manipulated-devices-list
                                       update-in [:devices]
                                       (fn [coll]
                                         (sort-by :name
                                                  (if asc? compare (comp (fn [n] (* -1 n)) compare))
                                                  coll)))
                                (swap! ascending? not))
         :label            (if asc? 
                             [:i.fa.fa-sort-alpha-asc] 
                             [:i.fa.fa-sort-alpha-desc])]))))


(defn refresh-btn [configs]
  (let [devices-list-m (:devices-list configs)
        refresh-fn (:refresh-fn devices-list-m)
        loading-a (:loading-a devices-list-m)
        error-a (:error-a devices-list-m)]
    [re/button
     :tooltip "Check for new devices (WhoIs)"
     :tooltip-position :below-right
     :on-click (fn [] (when refresh-fn
                        (refresh-fn)))
     :label [:i.fa.fa-refresh 
             {:class (str (when @loading-a "fa-pulse ")
                          (when @error-a "text-danger"))}]]))




(defn left-side-nav-bar
  [dev-list selected-device-id configs]
  (let [show-ids? (r/atom nil)
        local-click (r/atom nil)
        devices-list (r/atom (for [{:keys [device-id device-name]} dev-list]
                               {:id device-id :name device-name}))
        manipulated-devices-list (r/atom {:devices @devices-list
                                          :previous-filter ""})
        select-device! (fn [id]
                         (do 
                           (when-not (:vigilia-mode configs) 
                             (routes/replace-hash! 
                              (routes/path-for :devices-with-id :device-id id)))
                             (reset! selected-device-id id)))]
                           
    (fn [dev-list selected-device-id configs]
      (let [ids? @show-ids?
            devices @devices-list
            visible-ids (map :id (:devices @manipulated-devices-list))
            visible-ids-max-length (apply max (map count visible-ids))]
        (when-not (some #{@selected-device-id} (map :id devices))
          (when (seq devices)
            (select-device! (:id (first devices)))))
        [re/v-box
         :class    "noselect"
         :size     "1"
         :gap      "5px"
         :children [[re/title 
                     :level :level2
                     :underline? true
                     :label [:span {:style {:white-space :nowrap}} "Devices " 
                             [:small "("
                              (count (:devices @manipulated-devices-list))"/"(count devices)")"]]]
                    [re/h-box
                     :gap "3px"
                     :children [[re/button 
                                 :label            (if ids? [:i.fa.fa-dedent] [:i.fa.fa-indent])
                                 :on-click         #(do (reset! local-click true)
                                                        (swap! show-ids? not))
                                 :tooltip          "Show device IDs"
                                 :tooltip-position :below-right]
                                [refresh-btn configs]
                                [re/gap :size "1"]
                                [sort-by-id-btn manipulated-devices-list local-click]
                                [sort-by-name-btn manipulated-devices-list local-click]]]
                    [re/input-text 
                     :model       ""
                     :width       "100%" 
                     :change-on-blur? false
                     :on-change   #(reset! manipulated-devices-list 
                                           {:devices (filter-devices @devices-list %)
                                            :previous-filter %})
                     :placeholder "Filter"]
                    [re/gap :size "5px"]
                    [re/scroller ;common/scrollable 
                     :size "1"
                     :child
                     
                     [:div 
                      {:class "left-side-navbar"
                       :style {:white-space "nowrap"}}
                      (if (seq devices)
                        (doall 
                         (for [tab (:devices @manipulated-devices-list)]
                           ^{:key (gensym (:id tab))}
                           [nav-item tab selected-device-id show-ids? 
                            visible-ids-max-length local-click configs]))
                        [:div [:div "No devices found."]
                         [:div " Check the local device "
                          [:a {:href (routes/path-for :local-device-configs)} "configs"]]])]]]]))))



;; ;=======================================================
;; ;================= Main view (objects) =================
;; ;=======================================================


(defn object-icon [object-type]
  (when-let [icon (wo/object-icon object-type)]
    [:span {:title (wo/object-type-name object-type)}
     icon]))



(defn properties-btn [vigilia-object configs]
  (let [ok-btn [:button {:on-click mod/close-modal!
                         :style {:margin-right "15px"}
                         :class "btn btn-primary"}
                "Ok"]]
    [:button {:class "btn btn-default btn-sm"
              :title "Properties"
              :on-click (fn []
                          (mod/modal!
                           [wo/prop-modal vigilia-object configs ok-btn]
                           {:size :lg}))}
     [:i.fa.fa-list]]))

(defn action-btns [row modal-content show? configs all-objects-a]
  (let [object row]
    [:div
     ;[graph-btn v-object modal-content show?]
     [properties-btn object configs]
     (when-not (= (:object-type object) "8")
       [:button.btn.btn-default.btn-sm
        {:title "Delete"
         :on-click (fn []
                     (let [callback (fn []
                                      (swap! all-objects-a update-in [:objects]
                                             (fn [objs]
                                               (prn objs)
                                               (remove #(= (:object-id %)
                                                           (:object-id object)) objs)))
                                      (mod/close-modal!))]
                       (mod/modal! [wo/delete-object-modal object
                                    configs callback mod/close-modal!])))}
        [:i.fa.fa-trash]])]))



(def Table (r/adapt-react-class js/FixedDataTable.Table))
(def Column (r/adapt-react-class js/FixedDataTable.Column))
(def Cell (r/adapt-react-class js/FixedDataTable.Cell))

(defn actions-cell [bacnet-object modal-content show? configs all-objects-a]
  [Cell [action-btns bacnet-object modal-content show? configs all-objects-a]])



(defn inside-value-cell [bacnet-object cell-comp current-value-a loading-a error-a]
  (let [flash-update (fn [comp]
                       (let [node (r/dom-node comp)
                             class-list (.-classList node)
                             class-name "flash"]
                         (when @current-value-a
                           (.add class-list class-name)
                           (js/setTimeout #(.remove class-list class-name) 1000))))]
    (r/create-class 
     {:component-did-update #(flash-update cell-comp)
      :reagent-render 
      (fn [bacnet-object cell-comp current-value-a loading-a error-a]
        (let [present-value (:present-value (or (get @current-value-a (:object-id bacnet-object))
                                                bacnet-object))]
          [:div {:style {:margin-left 5
                         :margin-right 5
                         :white-space "nowrap"}}
           (if @error-a 
             [:span.text-danger "Error " [:i.fa.fa-fw.fa-exclamation-triangle]]
             
             (wo/object-value (:object-type bacnet-object) present-value))
           (when @loading-a [:i.fa.fa-fw.fa-spinner.fa-pulse])]))})))

(defn value-cell
  "The value cell can be initiated with a given :present-value, but will
  update itself periodically and flash to give a feedback to the
  user."
  [bacnet-object]
  (let [bacnet-object-a (atom bacnet-object)
        current-value-a (r/atom nil)
        loading? (r/atom nil)
        error? (r/atom true)
        forget-me? (atom nil)
        reload-chan (chan)
        get-new-value! (fn []
                         (reset! loading? true)
                         (reset! error? nil)
                         (GET (:href @bacnet-object-a)
                             {:handler (fn [response]
                                         (reset! loading? nil)
                                         (reset! current-value-a 
                                                 {(:object-id @bacnet-object-a) response}))
                              :response-format :transit
                              :params {:properties [:present-value]}
                              :error-handler #(do (reset! error? true) 
                                                  (reset! loading? nil)
                                                  (prn %))}))
        reload-loop! (fn []
                       (go 
                         (while (not @forget-me?)                           
                           (async/alts! [reload-chan (async/timeout 20000)])
                           (when (not @forget-me?)
                             (get-new-value!)))))]
    (r/create-class
     {:display-name "present value cell"
      :component-will-unmount (fn [this]                          
                                (reset! forget-me? true))
      :component-did-mount reload-loop!
      
      :reagent-render
      (fn [bacnet-object]
        ;; we might get a different bacnet-object every time we
        ;; scroll. Reset everything to zero.
        (reset! bacnet-object-a bacnet-object)
        ;; By the same token, fetch the value for the bacnet object
        ;; currently in this cell.
        (put! reload-chan :reload-now)
        ;; every deref is done in an sub-component so that we don't
        ;; mess up the updates done by :component-did-mount and
        ;; :component-did-update.
        [Cell [inside-value-cell bacnet-object (r/current-component) 
               current-value-a loading? error?]])})))
              



(defn make-table [all-objects-a visible-objects-a device-id configs]
  (let [component (atom nil)
        width (r/atom 0)
        height (r/atom 0)
        forget-me? (atom nil)
        ;;;;
        show-modal? (r/atom nil)
        modal-content (r/atom "")
        vigilia-mode (:vigilia-mode configs)
        ;; because fixeddatatable isn't reponsive, we have to make
        ;; sure to resize it manually whenever the user resize
        ;; something.
        resize-fn (fn this-fn [old-width old-height node]
                    (when-not (or @forget-me? vigilia-mode)
                      (let [new-width (.-offsetWidth node)
                            new-height (.-offsetHeight node)]
                        (timer/callOnce
                           (fn []
                             (js/window.requestAnimationFrame #(this-fn new-width new-height node))) 50)
                          ;; only update when the user stopped resizing
                          (when (= old-width new-width)
                            (reset! width new-width))
                          (when (= old-height new-height)
                            (reset! height new-height)))))]
    (r/create-class
     {:component-did-mount #(let [node (r/dom-node @component)
                                  w (.-offsetWidth node)
                                  h (.-offsetHeight node)]
                              (reset! width w)
                              (reset! height h)
                              (resize-fn w h node))
      :component-will-unmount #(reset! forget-me? true)
      :reagent-render
      (fn [all-objects-a visible-objects-a device-id configs]
        (let [table-data (vec (sort-by :object-name (:objects @visible-objects-a)))
              this-c (r/current-component)
              make-cell (fn [component-fn]
                          (fn [args]
                            (let [{:strs [columnKey rowIndex]} (js->clj args)
                                  cell-value (get-in table-data [rowIndex (keyword columnKey)])]
                              (r/as-element [component-fn cell-value]))))
              cell-object-name (fn [args]
                                 (let [{:strs [rowIndex]} (js->clj args)
                                       cell-value (get-in table-data [rowIndex])
                                       draggable? (:present-value cell-value)
                                       object-name (let [n (:object-name cell-value)]
                                                     (if (empty? n) "< no name >" n))]
                                   (r/as-element 
                                    [Cell (merge {:style (merge {:white-space "nowrap"
                                                                 :width "100%"}
                                                                (when draggable? {:cursor :move}))}
                                                 (when draggable?
                                                   {:draggable true
                                                    :on-drag-start #(-> (.-dataTransfer %)
                                                                        (.setData "application/edn" 
                                                                                  (str 
                                                                                   {:wacnet-object 
                                                                                    (into {} cell-value)})))}))
                                     [:span {:title object-name}
                                      (when draggable? 
                                        [:span {:style {:margin-right "5px"}} 
                                         [:i.fa.fa-ellipsis-v]
                                         [:i.fa.fa-ellipsis-v]])
                                      object-name]])))
              cell-type (make-cell (fn [cell-value]
                                     [Cell [object-icon cell-value]]))
              cell-generic (make-cell (fn [cell-value]
                                        (let [cell-value (if (keyword? cell-value)
                                                           (name cell-value)
                                                           cell-value)]
                                          [Cell {:title cell-value
                                                 :style {:white-space "nowrap"
                                                         :margin-left 5}} cell-value])))              
              cell-actions (fn [args]
                             (let [{:strs [rowIndex]} (js->clj args)
                                   cell-value (get-in table-data [rowIndex])]
                               (r/as-element [actions-cell cell-value modal-content show-modal? configs
                                              all-objects-a])))
              cell-present-value (fn [args]
                                   (let [{:strs [rowIndex]} (js->clj args)
                                         cell-value (get-in table-data [rowIndex])]
                                     (r/as-element [value-cell cell-value])))

              vigilia-mode (:vigilia-mode configs)
              cell-checkbox (make-cell (fn [cell-value]
                                         (let [o-id cell-value
                                               ids-record-a (r/cursor vigilia-mode [:target-objects 
                                                                                    device-id])]
                                           [Cell [:span
                                                  [:input 
                                                   {:type :checkbox
                                                    :checked (some #{o-id} @ids-record-a)
                                                    :on-change #(swap! ids-record-a
                                                                       (fn [ids-record]
                                                                         (-> (if (-> % .-target .-checked)
                                                                               (conj ids-record o-id)
                                                                               (remove #{o-id} ids-record))
                                                                             (vec))))}]]])))

              ]
          (reset! component this-c)
          ;(prn @all-objects-a)
          ;(prn @vigilia-mode)
          
          [re/v-box
           :size "1"
           :style {;:width "100%"
                   :visibility (when-not (> @width 1) ;; don't show when the table is 0 px (initiating)
                                 "hidden")}
           :children 
           [(when @show-modal?
              [re/modal-panel
               :backdrop-on-click (handler-fn (do (reset! show-modal? false)
                                                  (reset! modal-content "")))
               :child @modal-content])            
            [Table
             {:width        @width
              :max-height   @height
              :rows-count    (count table-data)
              :header-height 40
              :row-height    40}
             (when vigilia-mode 
               [Column {:header "Record?"
                        :cell cell-checkbox
                        :columnKey :object-id
                        :width 100
                                        ;:flex-grow 1
                        }])
             [Column {:header "Type" 
                      :cell cell-type
                      :columnKey :object-type
                      :width 75}]
             [Column {:header "Name"
                      :cell cell-object-name
                      :columnKey :object-name
                      :width 150 
                                        ;:flex-grow 1
                      }]
             [Column {:header "Description"
                      :columnKey :description
                      :cell cell-generic
                      :width 100 :flex-grow 2}]
             (when-not vigilia-mode 
               [Column {:header "Value"
                                        ;:columnKey :present-value
                        :cell cell-present-value
                        :width 100}])
             (when-not vigilia-mode 
               [Column {:header "Units"
                        :cell cell-generic
                        :columnKey :units
                        :width 75}])
             (when-not vigilia-mode
               [Column
                {:header (r/as-element 
                          [Cell [:span "Action"
                                 [:button.btn.btn-default.btn-xs 
                                  {:style {:margin-left "10px"}
                                   :title "Create object"
                                   :on-click 
                                   (fn []
                                     (mod/modal!
                                      [wo/create-new-object-modal device-id configs
                                              (fn [resp]
                                                (let [{:keys [object-instance object-type]} resp]
                                                  (swap! all-objects-a update-in [:objects]
                                                         conj resp))
                                                (mod/close-modal!))
                                       mod/close-modal!]))}
                                  "+"]]])
                 :cell cell-actions
                 :width 130}])]]]))})))




(defn summary-detail [summary]
  (let [{:keys [vendor-name
                vendor-identifier
                description
                model-name]}      summary
        wiki-link (fn [& ns]
                    (let [ns (->> (remove nil? ns)
                                  (map s/lower-case)
                                  (map #(s/replace % #"," ""))
                                  (map #(s/replace % #" " "_"))
                                  (map #(s/replace % #":" "_"))
                                  (map #(s/replace % #"-" "_")))]
                      (str "https://wiki.hvac.io/doku.php?id=" (s/join ":" ns))))]
    [re/v-box
     :class "summary-detail"     
     :children [(when-not (empty? description)
                  [re/label :label description])
                (when-not (empty? description)
                  [re/line :class "separator" :color nil])
                (when-not (empty? description)
                  [re/gap :size "5px"])
                [re/h-box
                 :children [
                            ;[re/gap :size "20px"]
                            ;[re/line :class "separator" :color nil]
                            ;[re/gap :size "20px"]
                            [re/v-box
                             :children [[re/box :size "auto" 
                                         :child [:span (str "Vendor " ": ")
                                                 [:a {:href (wiki-link "suppliers" vendor-name)
                                                      :target "_blank"} vendor-name]
                                                 " "[:i.fa.fa-external-link]]]
                                        [re/box :size "auto" 
                                         :child [:span (str "Model " ": ")
                                                 [:a {:href (wiki-link "suppliers" 
                                                                       vendor-name 
                                                                       model-name)
                                                      :target "_blank"} model-name]
                                                 " "[:i.fa.fa-external-link]]]]]]]]]))
             


(defn filtering-bar [filter-string]
  [re/v-box
   :children [;; [re/h-box :children [[:span.field-label "asdad"]
              ;;                      [re/info-button
              ;;                       :info "popup info"]]]
              [re/input-text 
               :model       filter-string
               :width       "100%"
               :change-on-blur? false
               :on-change   #(reset! filter-string %)
               :placeholder "Filter"]]])

(defn filter-by-type
  "Given a list of objects, keep only those for which the key in
  filter-types-map has a non-nil value. If none have any values, do
  not filter." [objects filter-types-map]
  (let [bacnet-objects-for-types {"0" #{:analog :input}
                                  "1" #{:analog :output}
                                  "2" #{:analog :value}
                                  "3" #{:binary :input}
                                  "4" #{:binary :output}
                                  "5" #{:binary :value}
                                  "13" #{:multi-state :input}
                                  "14" #{:multi-state :output}
                                  "12" #{:loop}}]
    (if-not (some identity (vals filter-types-map)) ;;no filter
      objects
      (let [accepted-types (->> (for [[k v] filter-types-map]
                                  (when v k))
                                (remove nil?)
                                (set))]
        (filter #(cset/subset? accepted-types (get bacnet-objects-for-types (:object-type %))) objects)))))
      

(defn object-type-filter [type-filter-a]
  (let [set-all-fn (fn [value]
                     (reset! type-filter-a
                             (into {}
                                   (for [[k v] @type-filter-a]
                                     [k value]))))
        ;select-all-fn (fn [] (set-all-fn true))
        select-none-fn (fn [] (set-all-fn nil))
        analog-cur (r/cursor type-filter-a [:analog])
        binary-cur (r/cursor type-filter-a [:binary])
        input-cur (r/cursor type-filter-a [:input])
        output-cur (r/cursor type-filter-a [:output])
        loop-cur (r/cursor type-filter-a [:loop])]
    [re/h-box
     :style {:flex-flow "row wrap"}
     :children [[re/h-box
                 :children [[:button {:class (str "btn btn-default btn-sm"
                                                  (when @analog-cur " active"))
                                      :title "Analog"
                                      :on-click #(swap! analog-cur not)}
                             [:span [:i.fa.fa-signal]" Analog"]]
                            [:button {:class (str "btn btn-default btn-sm"
                                                  (when @binary-cur " active"))
                                      :title "Binary"
                                      :on-click #(swap! binary-cur not)}
                             [:span [:i.fa.fa-adjust]" Binary"]]
                            [:button {:class (str "btn btn-default btn-sm"
                                                  (when @input-cur " active"))
                                      :title "Input"
                                      :on-click #(swap! input-cur not)}
                             [:span [:i.fa.fa-sign-in]" Input"]]
                            [:button {:class (str "btn btn-default btn-sm"
                                                  (when @output-cur " active"))
                                      :title "Output"
                                      :on-click #(swap! output-cur not)}
                             [:span [:i.fa.fa-sign-out]" Output"]]
                            [:button {:class (str "btn btn-default btn-sm"
                                                  (when @loop-cur " active"))
                                      :title "Control loop"
                                      :on-click #(swap! loop-cur not)}
                             [:span [:i.fa.fa-circle-o-notch]" Control loop"]]]]
                
                [re/gap :size "1"]
                [re/h-box
                 :children [[:button {:class "btn btn-default btn-sm"
                                      :on-click select-none-fn}
                             [:span "Select none"]]
                            ;; [:button {:class "btn btn-default btn-sm"
                            ;;           :on-click select-all-fn}
                            ;;  [:span (t/t @t/locale :vigilia/select-all)]]
                            ]]]]))


(defn objects-table [selected-device-id configs]
  (let [objects-a (r/atom {})
        get-objects! (fn [device-id]
                       (GET (api-path (:api-root configs)
                                      "bacnet"
                                      "devices" device-id
                                      "objects")
                           {:handler #(reset! objects-a 
                                              {:id device-id
                                               :objects (for [o (:objects %)]
                                                          (let [[o-type o-inst] 
                                                                (s/split (:object-id o) #"\.")]
                                                            (assoc o :object-type o-type :object-instance o-inst)))})
                            :response-format :transit
                            :params {:properties [:object-name :description :units :present-value]}
                            :error-handler prn}))
        filter-string (r/atom "")
        filter-fn (fn [objects filter-string]
                    (let [regexp (re-pattern (util/make-fuzzy-regex filter-string))]
                      (filter #(->> ((juxt :object-name :description :present-value :units) %)
                                    (remove nil?)
                                    (s/join)
                                    (re-find regexp)) objects)))
        type-filter (r/atom {:analog nil
                             :binary nil
                             :input nil
                             :output nil})
        visible-objects-a (r/atom @objects-a)]
    (fn []
      (let [{:keys [id objects]} @objects-a
            device-id @selected-device-id            
            current-id? (= id device-id)]        
        (when-not current-id?
          (get-objects! device-id))
        (let [filtered-objects (-> (filter-fn objects @filter-string)
                                   (filter-by-type @type-filter))]
          (reset! visible-objects-a {:id id :objects filtered-objects})
          [re/v-box
           :size "1"
           :children 
           [[re/v-box
             :class (when objects (if-not (seq filtered-objects) "bg-danger"))
             :style {:padding "10px"
                     :padding-top "0px"}
             :children [[filtering-bar filter-string]
                        [object-type-filter type-filter]
                        [re/label :label [:span
                                          {:class (or (when objects (if-not (seq filtered-objects) 
                                                                      "text-danger"))
                                                      "field-label")}
                                          "Visible objects "
                                          ": "(count filtered-objects) "/" (count objects) ]]]]
            [re/box
             :style {:opacity (when-not current-id? "0.5")}
             :size "1"
             :child (if-not current-id?
                      [re/throbber]
                                        ;[:span "hello"]
                                        ;[:div "Insert Table"]
                      
                      [make-table objects-a visible-objects-a device-id configs]
                                        ;[home-page]
                      )]]])))))

    


(defn objects-view [selected-device-id configs]
  (let [device-summary (r/atom {:id nil :summary {}})
        get-summary! (fn [id]
                       (GET (api-path (:api-root configs)
                                      "bacnet"
                                      "devices" id)
                           {:handler #(reset! device-summary {:id id :summary %})
                            :response-format :transit
                            :error-handler prn}))]
    (fn [selected-device-id configs]
      (let [selected-id @selected-device-id
            {:keys [summary id]} @device-summary
            correct-summary? (= selected-id id)]
        ;; make sure we have the correct device summary
        (when-not correct-summary? (get-summary! selected-id))
        [re/v-box 
         :size "1"
         :style {:opacity (when-not correct-summary? "0.5")
                 :margin-right "10px"
                 :margin-left "5px"}
         :children [
                    [re/title
                     :level :level2
                     :underline? true
                     :label [:span [:i.fa.fa-caret-right] " " (:device-name summary)]]
                    [summary-detail summary]
                    [re/gap :size "20px"]
                    ;; only show table after we loaded the summary
                    (when correct-summary?
                      [objects-table selected-device-id configs]
                      )
                    ]]))))


    

(defn devices [selected-device-id configs]
  (let [dev-list-a (r/atom nil)
        error? (r/atom nil)
        loading? (r/atom true)
        load-devices-list! (fn this-fn
                             ([] (this-fn nil))
                             ([force-refresh?]
                              (reset! error? nil)
                              (reset! loading? true)
                              (GET (api-path (:api-root configs) "bacnet" "devices")
                                  {:handler (fn [response] 
                                              (js/setTimeout #(reset! loading? nil) 500)
                                              (reset! dev-list-a (:devices response)))
                                   :params (when force-refresh? {:refresh true})
                                   :response-format :transit
                                   :error-handler #(do (reset! error? %)
                                                       (reset! loading? nil)
                                                       (prn %))})))]
    (r/create-class
     {:component-did-mount (fn [_] (load-devices-list!))
      :reagent-render
      (fn [selected-device-id configs]
        (let [devices-list @dev-list-a]
          (cond
            devices-list
            [re/h-split
             ;:size "grow"
             :initial-split 20
             :panel-1 [left-side-nav-bar devices-list selected-device-id 
                       (assoc configs :devices-list {:device-list-a dev-list-a
                                                     :refresh-fn #(load-devices-list! :refresh)
                                                     :loading-a loading?
                                                     :error-a error?})]
             :panel-2 [objects-view selected-device-id configs]
             ]

            @error? [re/alert-box
                     :alert-type :warning
                     :body [:div 
                            [:h3 "Error"]
                            [:div (or (some-> @error? :response :error)
                                      (str @error?))]
                            [:div [:a {:href "#"
                                       :on-click load-devices-list!} "Retry"]
                             " or change the "
                             [:a {:href (routes/path-for :local-device-configs)}
                              "configs"]]]]
            @loading? [:div [re/throbber]]
            ;; when devices are found

            (not (seq devices-list))
            
            [:div.alert.alert-warning [:h3 "No remote devices found"]
             [:p "You can try to " [:a {:style {:cursor :pointer}
                                        :on-click load-devices-list!} "reload"] ", "
              "or change the local device " [:a {:href (routes/path-for :local-device-configs)} "configs"] "."]])))})))

  

(def default-configs
  {:api-root "/api/v1/"
   :device-table-btns ; each object (row) is applied to this function
   (fn [obj]
     (when (:present-value obj)
       [:div {:style {:white-space "nowrap"}}
        [:span.small
         {:on-click #(js/console.log "click")}
         [:i.fa.fa-briefcase]]]))})



(defn controllers-view
  ([device-id] (controllers-view device-id nil))
  ([device-id configs]
   (r/create-class
    {:display-name "controllers-view"
     ;; :should-component-update (fn [_ _ _]
     ;;                            true)
     :reagent-render
     (fn [device-id configs]
       ;; DON'T deref in this function, otherwise we always reset the
       ;; atom to the initial value.
                                        ;(reset! selected-device-id device-id)
       [devices (r/atom device-id) (merge default-configs configs)])})))

