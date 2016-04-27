(ns wacnet.templates.common
  (:require [re-com.core :as re]
            [reagent.core :as r]))

(defn is-chrome-or-opera?
  "True if the browser is Chrome(ium) or Opera"
  []
  (let [browser (-> js/goog .-labs .-userAgent .-browser)]
    (or (.isChrome browser)
        (.isOpera browser))))


(defn cond-h-split
  "If the browser is not Chrome or Opera, replace the split by a
  simple h-box." [& {:keys [panel-1 panel-2 size initial-split]}]
  (if (is-chrome-or-opera?)
    [re/h-split
     :initial-split initial-split
     :panel-1 panel-1
     :panel-2 panel-2]
     [re/h-box
     :size "1"
     :height "100%"
     :children [[re/box
                 :size (str initial-split)
                 :child panel-1]
                [re/box
                 :size (str (- initial-split 100))
                 :child panel-2]]]
     ))



(defn scrollable* 
  ([content] (scrollable* {} content))
  ([attr content]
   [:div (merge {:style {:overflow-y "auto"
                         :height "100vh"}} attr)
    content]))

(def scrollable
  (with-meta scrollable*
    {:component-did-mount
     #(let [node (r/dom-node %)
            top (.-top (.getBoundingClientRect node))]
        (set! (.-style.height node) (str "calc(100vh - "top"px)")))}))


;;;;;;;;;;;;;;;;;;;;;;


(defn input [{:keys [text on-save on-stop input-type]}]
  (let [val (r/atom text)
        stop #(do ;(reset! val "")
                  (if on-stop (on-stop)))
        save #(let [v (-> @val str clojure.string/trim)]
                (on-save v))]
    (fn [props]
      [input-type (merge
                  {:value @val :on-blur save
                   :class "form-control"
                   :style {:width "100%"}
                   :on-change #(reset! val (-> % .-target .-value))
                   :on-key-up #(case (.-which %)
                                 13 (save) ; enter
                                 27 (stop) ; esc 
                                 nil)}
                  props)])))

(def edit
  (-> input
      (with-meta {:component-did-mount
                  #(let [node (r/dom-node %)
                         n-value (count (.-value node))]
                     (.focus node)
                     (.setSelectionRange node n-value n-value)
                     )})))

(def edit-with-select
  (-> input
      (with-meta {:component-did-mount
                  #(let [node (r/dom-node %)
                         n-value (count (.-value node))]
                     (.focus node)
                     (.setSelectionRange node 0 n-value))})))


(defn save-edit-field [atom key value]
  (if-not (empty? value)
    (swap! atom assoc key value)
    (swap! atom dissoc key)))


(defn editable [input-type atom key]
  [edit
   {:text (get @atom key)
    :input-type input-type
    :on-save (partial save-edit-field atom key)}])

(defn editable-with-select [input-type atom key]
  [edit-with-select
   {:text (get @atom key)
    :input-type input-type
    :on-save (partial save-edit-field atom key)}])

(defn live-edit
  "Same as editable, but immediately updates the atom."
  [input-type atom key]
  [input-type {:value (get @atom key)
               :class "form-control"
               :style {:width "100%"}
               :on-change #(swap! atom assoc key (-> % .-target .-value))}])

;;; bootstrap 

(defn form-group [label id body]
  [:div.form-group.form-group-sm
   [:label.col-sm-6.control-label {:for id} label]
   [:div.col-sm-6 body]])
