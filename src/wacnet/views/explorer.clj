(ns wacnet.views.explorer
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout with-sidebar]]
            [hiccup.core :as h]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [bacure.core :as bac]
            [bacure.remote-device :as rd]
            [bacure.coerce :refer [c-object-type] :as c]))

(def ^{:dynamic true} *sidebar-device* nil) ;; the current remote device shown.
;(def ^{:dynamic true} *sidebar-object* nil) ;; the current remote object shown.

(def show-ambiguous
  "If this is set to true, the user will be able to see the devices'
ambiguous values. There isn't much to do with them, so we set it to
false by default."
  (atom false))

(defn toggle-show-ambiguous []
  (swap! show-ambiguous not))

(defn get-remote-devices-and-names
  "If any device name is missing, refresh the remote devices table. Give up after 'tries'."
  ([] (get-remote-devices-and-names 3)) ;;try 3 times
  ([tries]
     (let [rd-names (rd/remote-devices-and-names)]
       (if (and (some #(-> % second nil?) (rd/remote-devices-and-names))
                (> tries 0))
         (do (rd/discover-network)
             (get-remote-devices-and-names (dec tries)));;try again
         rd-names))))

(defn side-bar-devices
  "Return a list of devices (and their name) as :li elements."[]
  (let [devices-names (sort-by first (get-remote-devices-and-names))]
    (into [] (for [[d n] devices-names]
               [:li {:class (when (= *sidebar-device* (str d)) "active")}
                (he/link-to (str "/explorer/"d)
                            (str (or n "Uh-oh...") " (" d ")"))]))))

(defn sort-and-partition [coll]
  (->> (sort-by (juxt first last) coll)
       (partition-by first)
       (sort-by count)
       (sort-by (juxt ffirst))))

(defn objects-list
  "Return a list of objects as :div elements."[device-id]
  (let [device-id (Integer/parseInt device-id)
        separated (sort-and-partition (bac/remote-objects device-id))]
    (for [objects-list separated]
      (into [:div.span3 {:style "border: 1px solid #cecece; border-radius:5px; padding:1em;"}]
            (for [[type instance :as object] objects-list]
                           (let [object-name (apply :object-name
                                                    (bac/remote-object-properties device-id
                                                                                  object
                                                                                  [:object-name]))]
                             (he/link-to (str device-id "/" (.intValue (c-object-type type))"/" instance)
                                         [:div.btn (str (name type) " " instance) " - "
                                          object-name])))))))


(defn map-to-bootstrap [map]
  (for [m map]
    [:div.row-fluid {:style "border: 1px solid #cecece; border-radius:5px;"}
     [:div.span4
      [:p {:style "margin-left:1em;"}(name (key m))]]
     [:div.span8
      (let [v (val m)]
        (if (map? v)
          (map-to-bootstrap v)
          [:p {:style "margin-left:1em;"}
           (if (keyword? v) (name v)
               (str v))]))]]))
    

(defn properties-list [device-id type instance]
  (let [device-id (Integer/parseInt device-id)
        type (Integer/parseInt type)
        instance (Integer/parseInt instance)
        properties (first (bac/remote-object-properties device-id [type instance] [:all]))]
    (map-to-bootstrap properties)))

    
  
(defn explorer-layout [& body]
  (layout
   (with-sidebar {:header "Devices"
                  :sidebar (side-bar-devices)
                  :body body})))

(defroutes explorer-routes

  (GET "/explorer" []
       (explorer-layout [:div.hero-unit [:h2 "Welcome to your BACnet network!"]
                         "From here, you can browse the BACnet network and explore your devices."]))

  (GET "/explorer/:device-id" [device-id]
       (binding [*sidebar-device* device-id
                 c/*drop-ambiguous* (not @show-ambiguous)]
         (explorer-layout
          [:div.hero-unit [:h2 (str "Objects for device "device-id)]
           (for [row (partition-all 4 (objects-list device-id))]
             [:div.row-fluid {:style "margin-bottom: 1em;"} row])])))

  (GET "/explorer/:device-id/:type/:instance" [device-id type instance]
       (binding [*sidebar-device* device-id
                 c/*drop-ambiguous* (not @show-ambiguous)]
         (explorer-layout
          [:div.hero-unit [:h2 (str "Object properties:")]
           (properties-list device-id type instance)]))))
