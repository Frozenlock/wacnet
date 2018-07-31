(ns wacnet.explorer.devices
  (:require [reagent.core :as r]
            [cljs.reader :as reader]
            [ajax.core :refer [GET POST]]          
            [wacnet.utils :as u]
            [wacnet.templates.common :as common]
            [wacnet.explorer.objects :as wo]
            [goog.string :as gstring]
            [wacnet.routes :as routes]
            [clojure.string :as s]
            [re-com.core :as re :refer-macros [handler-fn]]
            [goog.Timer :as timer]
            [cljsjs.fixed-data-table-2]
            [clojure.set :as cset]            
            [reagent-modals.modals :as mod]
            [cljs.core.async :as async :refer [<! >! chan put! onto-chan]]
            [wacnet.stateful :as state])
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
              tab-name (let [n (:name tab)]
                         (if (empty? n) "< no name >" n))
              ids? @show-ids?]
          (reset! this-id (:id tab))
          [:div.nav-item
           {:class (when selected? "selected")
            :title tab-name

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
           [:span tab-name]]))})))



(defn filter-devices [devices-list filter-string]
  (let [regexp (re-pattern (u/make-fuzzy-regex filter-string))]
    (filter #(or (re-find regexp (or (:id %) "< no name >"))
                 (re-find regexp (or (:name %) "< no name >"))) devices-list)))


(defn sort-by-id-btn [sort-fn-a local-click]
  (let [ascending? (r/atom true)]
    (fn []
      (let [asc? @ascending?]
        [re/button
         :tooltip          "Sort by device ID"
         :tooltip-position :below-left
         :on-click         #(do (reset! local-click true)
                                (reset! sort-fn-a
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


(defn sort-by-name-btn [sort-fn-a local-click]
  (let [ascending? (r/atom true)]
    (fn []
      (let [asc? @ascending?]
        [re/button
         :tooltip          "Sort by device name"
         :tooltip-position :below-left
         :on-click         #(do (reset! local-click true)
                                (reset! sort-fn-a
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
  [dev-list-a selected-device-id configs]
  (let [show-ids? (r/atom nil)
        local-click (r/atom nil)
        clean-list-fn (fn [list-a] (for [{:keys [device-id device-name]} @list-a]
                                     {:id device-id :name device-name}))
        filter-a (r/atom "")
        sort-fn-a (r/atom nil)
        get-visible-devices (fn [] 
                              {:devices (let [d (filter-devices (clean-list-fn dev-list-a)
                                                                @filter-a)]
                                          (if-let [f @sort-fn-a]
                                            (f d) d))})
        manipulated-devices-list (r/track get-visible-devices)
        select-device! (fn [id]
                         (do 
                           (when-not (:vigilia-mode configs) 
                             (routes/replace-hash! 
                              (routes/path-for :devices-with-id :device-id id)))
                             (reset! selected-device-id id)))]
                           
    (r/create-class
     {:component-did-update #()
      :reagent-render
      (fn [dev-list-a selected-device-id configs]
        (let [ids? @show-ids?
              devices (clean-list-fn dev-list-a)
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
                                  [sort-by-id-btn sort-fn-a local-click]
                                  [sort-by-name-btn sort-fn-a local-click]]]
                      [re/input-text 
                       :model       ""
                       :width       "100%" 
                       :change-on-blur? false
                       :on-change   #(reset! filter-a %)
                       :placeholder "Filter"]
                      [re/gap :size "5px"]
                      [re/scroller     ;common/scrollable 
                       :size "1"
                       :class "left-side-navbar-container"
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
                            [:a {:href (routes/path-for :local-device-configs)} "configs"]]])]]]]))})))



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

(defn action-btns
  [object-a modal-content show? configs all-objects-a]
  (let [object @object-a]
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
                                               (remove #(= (:object-id %)
                                                           (:object-id object)) objs)))
                                      (mod/close-modal!))]
                       (mod/modal! [wo/delete-object-modal object
                                    configs callback mod/close-modal!])))}
        [:i.fa.fa-trash]])]))



(def Table (r/adapt-react-class js/FixedDataTable.Table))
(def Column (r/adapt-react-class js/FixedDataTable.Column))
(def Cell (r/adapt-react-class js/FixedDataTable.Cell))




(defn inside-value-cell [bacnet-object-a cell-comp current-value-a loading-a error-a]
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
      (fn [bacnet-object-a cell-comp current-value-a loading-a error-a]
        (let [present-value (:present-value (or (get @current-value-a (:object-id @bacnet-object-a))
                                                @bacnet-object-a))]
          [:div {:style {:margin-left 5
                         :margin-right 5
                         :white-space "nowrap"}}
           (if @error-a 
             [:span.text-danger "Error " [:i.fa.fa-fw.fa-exclamation-triangle]]
             
             (wo/object-value (:object-type @bacnet-object-a) present-value))
           (when @loading-a [:i.fa.fa-fw.fa-spinner.fa-pulse])]))})))

(defn value-cell
  "The value cell can be initiated with a given :present-value, but will
  update itself periodically and flash to give a feedback to the
  user."
  [cell-value-a]
  (let [bacnet-object-a (atom @cell-value-a)
        current-value-a (r/atom nil)
        loading? (r/atom nil)
        error? (r/atom true)
        forget-me? (atom nil)
        reload-chan (chan)
        get-new-value! (fn []
                         (when-let [href (:href @bacnet-object-a)]
                           (reset! loading? true)
                           (reset! error? nil)
                           (GET href
                               {:handler (fn [response]
                                           (reset! loading? nil)
                                           (reset! current-value-a 
                                                   {(:object-id @bacnet-object-a) response}))
                                :response-format :transit
                                :params {:properties [:present-value]}
                                :error-handler #(do (reset! error? true) 
                                                    (reset! loading? nil)
                                                    (prn %))})))
        reload-loop! (fn []
                       (go 
                         (while (not @forget-me?)
                           ;; reload value every 15 seconds
                           (async/alts! [reload-chan (async/timeout 15000)])
                           (when (and (not @forget-me?)
                                      @cell-value-a)
                             (get-new-value!)))))]
    (r/create-class
     {:display-name "present value cell"
      :component-will-unmount (fn [this]                          
                                (reset! forget-me? true))
      :component-did-mount reload-loop!
      
      :reagent-render
      (fn [cell-value-a]
        ;; we might get a different bacnet-object every time we
        ;; scroll. Reset everything to zero.
        (reset! bacnet-object-a @cell-value-a)
        ;; By the same token, fetch the value for the bacnet object
        ;; currently in this cell.
        (put! reload-chan :reload-now)
        ;; every deref is done in an sub-component so that we don't
        ;; mess up the updates done by :component-did-mount and
        ;; :component-did-update.
        [Cell [inside-value-cell bacnet-object-a (r/current-component) 
               current-value-a loading? error?]])})))              



(defn table-modal [show-modal? modal-content]
  (when @show-modal?
    [re/modal-panel
     :backdrop-on-click (fn [e]
                          (reset! show-modal? false)
                          (reset! modal-content ""))
     :child @modal-content]))

(defn column-sort
  ([column-name data-a data-path] (column-sort column-name data-a data-path nil))
  ([column-name data-a data-path is-number]
   (let [sort-direction (r/atom nil)]
     (fn [column-name data-a data-path]
       (let [sort-fn (fn [direction]
                       (swap! (r/cursor data-a [:objects])
                              (fn [m] (let [sorted (sort-by
                                                    (fn [o]
                                                      (let [value (get-in o data-path)]
                                                        (if-not is-number
                                                          value
                                                          (if (number? value) value 0))))
                                                    m)]
                                        (if (= direction :up)
                                          (do (reset! sort-direction :up)
                                              sorted)
                                          (do (reset! sort-direction :down)
                                              (reverse sorted)))))))]
         [Cell {:style {:width "100%"}}
          [re/h-box
           :align :center
           :children [[:span column-name]
                      [re/gap :size "1"]
                      [:span {:style {:position :relative
                                      :opacity 0.5}}
                       [:i.fa.fa-sort {:style {:opacity 0}}]
                       [:i.fa.fa-sort-up.fa-lg {:style {:position :absolute :right 3 :top 1
                                        ;:opacity (if (= @sort-direction :up) 1 0.5)
                                                        :cursor :pointer}
                                                :on-click #(sort-fn :up)}]                      
                       [:i.fa.fa-sort-down.fa-lg {:style {:position :absolute :right 3 :top 9
                                        ;:opacity (if (= @sort-direction :down) 1 0.5)
                                                          :cursor :pointer}
                                                  :on-click #(sort-fn :down)}]
                       ;; space between click areas
                       [:span {:style {:width 13 :height 9 :position :absolute :top 7 :right 3}}]]
                                        ;[re/gap :size "1px"]
                      ]]])))))


(defn table
  [& args]
  (let [sizes-a (r/atom {})]
    (r/create-class
     {:reagent-render
      (fn [objects-data-a device-id table-data-a size-a cell-type cell-object-name
           cell-generic cell-present-value cell-generic cell-actions cell-vigilia-checkbox
           configs]
        (let [vigilia-mode (:vigilia-mode configs)
              {:keys [width height]} @size-a]
          (if-not (> width 1)
            [:span]
            [Table
             {:width width
              :max-height height
              :rows-count    (count @table-data-a)
              :header-height 40
              :row-height    40
              :is-column-resizing false
              :on-column-resize-end-callback (fn [new-column-width column-key]
                                               (swap! sizes-a assoc (keyword column-key) new-column-width))}
             [Column
              {:header (r/as-element [column-sort "Name" objects-data-a [:object-name]])
               :columnKey :object-name
               :cell cell-object-name
               :is-resizable true
               :fixed true
               :width (or @(r/cursor sizes-a [:object-name]) 200)}]
             (when vigilia-mode
               [Column {:header "Record?"
                        :cell cell-vigilia-checkbox
                        :columnKey :object-id
                        :width 100}])
             [Column
              {:header (r/as-element [column-sort "Type" objects-data-a [:object-type]])
               :columnKey :object-type
               :cell cell-type
               :is-resizable true
                                        ;:is-reorderable true
               :width (or @(r/cursor sizes-a [:object-type]) 75)}]
             [Column
              {:header (r/as-element [column-sort "Description" objects-data-a [:description]])
               :cell cell-generic
               :is-resizable true
               :flex-grow 2
               :columnKey :description
               :width (or @(r/cursor sizes-a [:description]) 100)}]
             (when-not vigilia-mode
               [Column
                {:header (r/as-element [column-sort "Value" objects-data-a [:present-value]])
                 :columnKey :present-value
                 :is-resizable true
                 :cell cell-present-value
                 :width (or @(r/cursor sizes-a [:present-value]) 100)}])
             (when-not vigilia-mode
               [Column
                {:header (r/as-element [column-sort "Units" objects-data-a [:units]])
                 :columnKey :units
                 :is-resizable true
                 :cell cell-generic
                 :width (or @(r/cursor sizes-a [:units]) 75)}])
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
                                           (swap! objects-data-a update-in [:objects]
                                                  conj resp))
                                         (mod/close-modal!))
                                       mod/close-modal!]))}
                                  "+"]]])
                 :cell cell-actions
                 :width 135}])])))})))




(defn generic-cell [cell-prop-value-a]
  (let [v @cell-prop-value-a]
    [Cell {:title v
           :style {:white-space "nowrap"
                   :margin-left 5}}
     v]))

(defn icon-cell [cell-value-a]
  (let [ot @(r/cursor cell-value-a [:object-type])]
    [object-icon ot]))

(defn name-cell [cell-value-a]
  (let [cell-value @cell-value-a
        object-name (let [n (:object-name cell-value)]
                      (if (empty? n) "< no name >" n))
        draggable? (:present-value cell-value)]
    [Cell (merge {:style {:white-space "nowrap"
                          :width "100%"
                          ;; :text-overflow :ellipsis
                          ;; :overflow :hidden
                          :height "100%"
                          ;:background-color (when in-briefcase? "grey")
                          :cursor (when draggable? :move)}
                  :class (str "object-name-cell" (when draggable? " draggable"))
                  :on-click #(prn (into {} cell-value))}
                 (when draggable?
                   {:draggable true
                    :on-drag-start #(doto (.-dataTransfer %)
                                      (.setData "application/edn" 
                                                (str 
                                                 {:wacnet-object 
                                                  (into {} cell-value)}))
                                      (.setData "text"
                                                (->> cell-value
                                                     ((juxt :object-name :global-id :description))
                                                     (s/join "\t"))))}))
     [:span {:title object-name}
      (when draggable? 
        [:span.handle {:style {:margin-right "5px"}}
         [:i.fa.fa-ellipsis-v]
         [:i.fa.fa-ellipsis-v]])
      object-name]]))


(defn make-table [objects-store-a visible-objects-a device-id configs]
  (let [component (atom nil)
        show-modal? (r/atom nil)
        modal-content (r/atom "")
        debounce (u/debounce-factory)
        size-a (r/atom {:width 0 :height 0})]
    (r/create-class
     {:component-did-mount #(let [node (r/dom-node %)]
                              (reset! component node))
      :reagent-render
      (fn [objects-store-a visible-objects-a device-id configs]
        (let [cell-type (fn [args]
                          (let [{:strs [rowIndex]} (js->clj args)]
                            (r/as-element [Cell [icon-cell (r/cursor visible-objects-a [rowIndex])]])))
              cell-object-name (fn [args]
                                 (let [{:strs [rowIndex]} (js->clj args)]
                                   (r/as-element [name-cell (r/cursor visible-objects-a [rowIndex])])))              
              cell-present-value (fn [args]
                                   (let [{:strs [rowIndex]} (js->clj args)]
                                     (r/as-element [value-cell (r/cursor visible-objects-a [rowIndex])])))
              cell-generic (fn [args]
                             (let [{:strs [columnKey rowIndex]} (js->clj args)]
                               (r/as-element [generic-cell (r/cursor visible-objects-a [rowIndex (keyword columnKey)])])))
              cell-actions (fn [args]
                             (let [{:strs [rowIndex]} (js->clj args)]
                               (r/as-element [Cell                                              
                                              [action-btns
                                               (r/cursor visible-objects-a [rowIndex])
                                               modal-content
                                               show-modal?
                                               configs
                                               objects-store-a]])))
              vigilia-checkbox (fn [o-id-a ids-record-a]
                                 (let [o-id @o-id-a]
                                   [:span
                                    [:input
                                     {:type :checkbox
                                      :checked (if (some #{o-id} @ids-record-a) true false)
                                      :on-change #(swap! ids-record-a
                                                         (fn [ids-record]
                                                           (prn "check" (-> % .-target .-checked))
                                                           (-> (if (-> % .-target .-checked)
                                                                 (conj ids-record o-id)
                                                                 (remove #{o-id} ids-record))
                                                               (vec))))}]]))
              cell-vigilia-checkbox (fn [args]
                                      (let [{:strs [rowIndex]} (js->clj args)]
                                        (let [object-a (r/cursor visible-objects-a [rowIndex])
                                              o-id-a (r/cursor object-a [:object-id])
                                              ids-record-a (r/cursor (:vigilia-mode configs)
                                                                     [:target-objects @(r/cursor object-a [:device-id])])]
                                          (r/as-element
                                           [Cell [vigilia-checkbox o-id-a ids-record-a]]))))]
          
          [re/v-box
           :class "controllers"
           :size "1"
           :style {:height "100%"
                   :width "100%"}
           :children 
           [[table-modal show-modal? modal-content]
            [u/auto-sizer
             (fn [m]
               (debounce #(reset! size-a m) 20)
               [table objects-store-a device-id visible-objects-a size-a
                cell-type cell-object-name cell-generic cell-present-value cell-generic cell-actions
                cell-vigilia-checkbox
                configs])]]]))})))



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
               :placeholder "Filter (supports regex)"]]])

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
                             [:span "Select none"]]]]]]))


(defn make-regex-filter [filter-string-a]
  (try (->> @filter-string-a
            (str "(?i)")
            ;(u/make-fuzzy-regex)
            (re-pattern))
       (catch :default e)))

(defn filter-with-string [objects-store filter-string-a]
  (let [objects @(r/cursor objects-store [:objects])
        regexp @(r/track make-regex-filter filter-string-a)]
    (if regexp
      (filter #(->> ((juxt :object-name :description :present-value :units) %)
                    (remove nil?)
                    (s/join)
                    (re-find regexp)) objects)
      objects)))


(defn filter-objects [objects-store filter-string-a filter-type-a]
  (-> @(r/track filter-with-string objects-store filter-string-a)
      (filter-by-type @filter-type-a)
      (vec)))

(defn filter-header [objects-store filtered-objects-a filter-string-a filter-type-a]
  (let [filtered-objects @filtered-objects-a
        objects @(r/cursor objects-store [:objects])]
    [re/v-box
     :class (when objects (if-not (seq filtered-objects) "bg-danger"))
     :style {:padding "10px"
             :padding-top "0px"}
     :children [[filtering-bar filter-string-a]
                [object-type-filter filter-type-a]
                [re/label :label [:span
                                  {:class (or (when objects (if-not (seq filtered-objects) 
                                                              "text-danger"))
                                              "field-label")}
                                  "visible objects"
                                  ": "(count filtered-objects) "/" (count objects) ]]]]))



(defn objects-table [params-a selected-dev-a configs]
  (let [objects-store-a (r/atom {})
        visible-objects-a (r/atom {})
        filter-string-a (r/atom "")
        filter-type-a (r/atom {:analog nil
                               :binary nil
                               :input nil
                               :output nil})
        ;;
        loading? (r/atom nil)
        error? (r/atom nil)
        all-loaded? (r/atom nil)
        current-loaded-id (r/atom nil)
        get-remaining-objects!
        (fn get-remaining-objects!
          ([device-id]
           (reset! error? nil)
           (reset! loading? true)
           (get-remaining-objects! device-id 1))
          ([device-id page]
           (when-not (= @current-loaded-id device-id)
             (reset! objects-store-a {})
             (reset! all-loaded? nil))
           (reset! current-loaded-id device-id)
           (GET (api-path (:api-root configs)
                          "bacnet"
                          "devices" device-id
                          "objects")
               {:handler #(do 
                            (when (= @selected-dev-a device-id)
                              (if-let [n-page (get-in % [:next :page])]
                                (get-remaining-objects! device-id n-page)
                                (reset! all-loaded? true))
                              ;; only update if we are still looking at the same device
                              (reset! loading? nil)
                              (let [o-to-add (for [o (:objects %)]
                                               (let [[o-type o-inst] 
                                                     (s/split (:object-id o) #"\.")]
                                                 (assoc o :object-type o-type :object-instance o-inst)))]
                                (if (= device-id (:id @objects-store-a))
                                  (swap! objects-store-a update-in [:objects]
                                         (fn [objs]
                                           (distinct (concat objs o-to-add))))
                                  (reset! objects-store-a {:id device-id
                                                           :objects o-to-add})))))
                :response-format :transit
                :params {:properties [:object-name :description :units :present-value]
                         :limit 50
                         :page page}
                :error-handler #(when true;(= @selected-dev-a device-id)
                                  (reset! loading? nil)
                                  (reset! error? {:page page
                                                  :device-id device-id}))})))]
    (r/create-class
     {:component-did-mount (fn []
                             (get-remaining-objects! @selected-dev-a))
      :component-will-update (fn []
                               (let [{:keys [id objects]} @objects-store-a
                                     device-id @selected-dev-a
                                     current-id? (= id device-id)]
                                 (when-not current-id?
                                   (when-not (= @current-loaded-id (:device-id @error?) device-id)
                                     (get-remaining-objects! device-id)))))
      :reagent-render      
      (fn []
        (let [{:keys [id objects]} @objects-store-a
              device-id @selected-dev-a
              current-id? (= id device-id)]          
          (let [filtered-objects-a (r/track filter-objects objects-store-a filter-string-a filter-type-a)]
            [re/v-box
             :size "1"
             :children
             [[filter-header objects-store-a filtered-objects-a filter-string-a filter-type-a]
              [re/box
               :style {:opacity (when (and (not current-id?)
                                           (not @error?))
                                  "0.5")}
               :size "1"
               :class "unselectable"
               :child (cond @error?
                            (let [err @error?] 
                              [:div.alert.alert-danger "Error while trying to load objects page " (:page err) ". "
                               [:a {:on-click #(do (reset! error? nil)
                                                   (get-remaining-objects! device-id (:page err)))
                                    :style {:cursor :pointer}} "Retry"]
                               [:div (str "You can also check the model link above to see if there are " 
                                          "known BACnet issues with this product.")]])
                            @loading?
                            [re/throbber]

                            :else [make-table objects-store-a filtered-objects-a device-id configs])]]])))})))

    


(defn objects-view [params-a configs]
  (let [device-summary (r/atom {:id nil :summary {}})
        loading-a (r/atom nil)
        error-a (r/atom nil)
        get-summary! (fn [id]
                       (GET (api-path (:api-root configs)
                                      "bacnet"
                                      "devices" id)
                           {:handler (fn [resp]
                                       (reset! device-summary {:id id :summary resp})
                                       (reset! error-a nil)
                                       (reset! loading-a nil))
                            :response-format :transit
                            :error-handler (fn [resp]
                                             (reset! device-summary {:id id})
                                             (reset! error-a resp)
                                             (reset! loading-a nil))}))]
    (fn [params-a configs]
      (let [selected-dev-a (r/cursor params-a [:device-id])
            selected-id @selected-dev-a
            {:keys [summary id]} @device-summary
            correct-summary? (= selected-id id)]
        ;; make sure we have the correct device summary
        (when-not correct-summary? (get-summary! selected-id))
        [re/v-box
         :size "1"
         :style {:margin-right "10px"
                 :margin-left "5px"}
         :children [[re/v-box
                     :style {:opacity (when-not correct-summary? "0.5")}
                     :children [[re/title
                                 :level :level2
                                 :underline? true
                                 :label [:span [:i.fa.fa-caret-right] " "(:device-name summary)]]
                                [summary-detail summary]]]
                    [re/gap :size "20px"]
                    [objects-table params-a selected-dev-a configs]]]))))


    

(defn devices [params-a configs]
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
      (fn [params-a configs]
        (let [devices-list @dev-list-a
              selected-device-a (r/cursor params-a [:device-id])]
          (cond
            devices-list
            [re/h-split
             :initial-split 20
             :panel-1 [left-side-nav-bar dev-list-a
                       selected-device-a 
                       (assoc configs :devices-list {:device-list-a dev-list-a
                                                     :refresh-fn #(load-devices-list! :refresh)
                                                     :loading-a loading?
                                                     :error-a error?})]
             :panel-2 [objects-view params-a configs]]

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
  ([] (controllers-view {}))
  ([configs]
   (let [params-a (state/url-params-atom)]
     [devices params-a (merge default-configs configs)])))

