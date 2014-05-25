(ns wacnet.explorer
 (:require [reagent.core :as r]
           [ajax.core :refer [GET POST]]
           [query.core :as q]))

(enable-console-print!)

(defn api-str
  ([] "api/v1")
  ([& args] (str "api/v1/" (clojure.string/join "/" args))))

(def devices-list-atom (r/atom []))
(def objects-list-atom (r/atom []))

(defn set-interval
  "Invoke the given function after and every delay milliseconds."
  [delay f]
  (js/setInterval f delay))

(defn get-devices-list []
  (GET (api-str "devices-list") 
      {:handler #(do (reset! devices-list-atom %)
                     (.log js/console "Device list updated"))
       :error-handler #(.log js/console %)}))

(defn get-objects-list [device-id]
  (GET (str (api-str "objects-list") 
            (q/to-query {:device-id device-id}))
       {:handler #(do (reset! objects-list-atom %)
                      (.log js/console "Objects list updated"))
        :error-handler #(.log js/console %)}))

(defn get-object-props [object-a device-id obj-id props]
  (GET (str (api-str "object-props")
            (q/to-query {:device-id device-id
                         :object-id obj-id
                         :props props}))
       {:handler #(reset! object-a %)
        :error-handler #(prn %)}))

(defn sort-and-partition [coll]
  (->> (sort-by (juxt first last) coll)
       (partition-by first)
       (sort-by count)
       (sort-by (juxt ffirst))))

(defn sort-objects [coll]
  (sort-by (juxt first last) coll))

;;; rendering functions
(def reload-devices
  [:button.btn.btn-default.pull-right
   {:on-click #(GET (api-str "reload-devices"))}
   [:span.glyphicon.glyphicon-refresh]])


(defn devices-list [current-device]
  (let [l @devices-list-atom
        current @current-device]
    [:div [:h3 "Devices" reload-devices]
     [:div.list-group
      (for [[id name] l]
        ^{:key id}
        [:a.list-group-item {:class (when (= current id) "active")
                             :on-click #(do (reset! current-device id)
                                            (get-objects-list @current-device))
                             :style {:cursor "pointer"}}
         [:div id]
         [:div name]])]]))



(defn collapse-panel [heading body]
  (let [target (gensym)]
    [:div.panel-group
     [:div.panel.panel-default
      [:div.panel-heading.panel-toggle
       {:data-toggle "collapse"
        :data-target (str "#"target)
        :style {:cursor "pointer"}} heading]
      [:div.panel-collapse.collapse {:id target}
       [:div.panel-body body]]]]))


(defn map-to-bootstrap [title map]
  (collapse-panel
   title
   (for [m map]
     ^{:key (key m)}
     [:div.row
      [:div.col-sm-6
       [:div {:style {:margin-left "1em" }}(name (key m))]]
      [:div.col-sm-6
       (let [v (val m)]
         (if (map? v)
           (map-to-bootstrap (name (key m)) v)
           [:p {:style {:margin-left "1em"}}
            (if (keyword? v) (name v)
                (str v))]))]])))

(defn name* [k]
  (if (keyword? k)
    (name k)
    (str k)))

(defn print-properties [props]
  (let [{:keys [object-name description present-value units]} props
        title [:div {:style {:padding-left "1em"}}
               [:div.row
                [:div.col-sm-4 (name* object-name)]
                [:div.col-sm-2 (name* present-value)]
                [:div.col-sm-2 (name* units)]
                [:div.col-sm-4 (name* description)]]]]
    (when props
      (map-to-bootstrap title props))))


(defn device-object []
  (let [obj-device-a (r/atom nil)]
    (fn [device-id]
      (let [obj-device @obj-device-a]
        [:div
         (when-not obj-device 
           (get-object-props obj-device-a device-id [:device device-id] [:all])
           "<loading>")
         [:div (print-properties obj-device)]]))))


(defn object-detail []
  (let [object-a (r/atom nil)]
    (fn [device-id object-id]
      (let [props @object-a]
      [:div 
       [:div (when-not props
               (get-object-props object-a device-id object-id [:all])
               "<loading>")]
       (print-properties props)]))))

(defn device-details [current-device]
  (let [current @current-device
        objs (sort-objects @objects-list-atom)]
    [:div [:h3 "Objects for device " current]
     [:div ;[device-object current]
      (for [o objs]
        ^{:key o}
        [:div [object-detail current o]])]]))


(defn explorer []
  (let [current-device (r/atom nil)]
    [:div.container
     [:div.row
      [:div.col-md-3 
       [devices-list current-device]]
      [:div.col-md-9 [device-details current-device]]]]))
  




;;;; automatic updates

(defn init []
  (get-devices-list)
  (set-interval 3000 get-devices-list))

(init)
