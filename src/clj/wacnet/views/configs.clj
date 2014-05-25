(ns wacnet.views.configs
  (:require [compojure.core :refer [defroutes GET POST]]
            [clojure.walk :as walk]
            [wacnet.views.common :refer [layout with-sidebar]]
            [bacure.core :as bac]
            [bacure.local-device :as ld]
            [bacure.local-save :as save]
            [bacure.network :as net]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]))

(defn read-map [m]
  (binding [*read-eval* false]
    (let [f (fn [[k v]] [k (read-string v)])]
      (walk/postwalk (fn [x] (if (map? x) (into {} (map f x)) x)) m))))

(defn read-config
  "Convert the strings in the config map into numbers."[config]
  (let [broadcast-address (:broadcast-address config)
        local-address (:local-address config)
        read-config (-> (dissoc config :broadcast-address :local-address) ;;ip addresses must stay strings
                        read-map)]
    (assoc read-config
      :broadcast-address broadcast-address
      :local-address local-address)))

(defn reset-with-config
  "Reset the local-device with the new configs."[config]
  (ld/reset-local-device (read-config config))
  (ld/save-local-device-backup)
  (bac/boot-up))

(defn reset-default
  "Erased any saved config and reset to default."
  []
  (save/delete-configs)
  (ld/clear-all!)
  (bac/boot-up))

(defn config-to-bootstrap [config]
  (for [m config]
    (let [n (name (key m))]
      [:div.form-group
       [:label.control-label.col-md-4 n]
       [:div.col-md-8
        (let [v (val m)]
          [:input.form-control {:type :text :name n :value v}])]])))

(defn make-configs-forms []
  (-> (merge {:device-id 1 :port 47808 
              :broadcast-address (net/get-broadcast-address (net/get-any-ip))}
             (ld/local-device-backup))
      (select-keys [:device-id :port :broadcast-address])
      config-to-bootstrap))

(defn local-interfaces []
  [:p {:style "margin-top:2em;"}
    [:h2 "Local interfaces"]
    (->> (net/interfaces-and-ips)
         (map #(vector :li [:b (:interface %)] ": " 
                       (clojure.string/join ", " 
                                            (:ips %))
                       (str "\nBroadcast: " (:broadcast-address %)))))])

(defn config-page [& msg]
  [:div.container
   [:div.hero-unit
    [:h1 "Local device configurations"]
    (when msg [:span.label.label-success (first msg)])
    [:form.form-horizontal {:method "POST" :action "" :role "form"}
     (make-configs-forms)
     [:div.text-center
      (hf/submit-button {:class "btn btn-primary" :style "margin-right: 1em;"}
                        "Validate and reinitialize the local device!")
      (he/link-to (hiccup.util/url "/configs" {:reset true})
                  [:div.btn.btn-danger "Reset default"])]]]
   [:div.text-center {:style "margin-top:2em;"}
    (when (save/get-configs)
      [:div.badge.badge-warning "You are using a saved config file."])]
   [:hr]
   (local-interfaces)])
  
(defroutes configs-routes
  (GET "/configs" [reset]
       (when reset (reset-default))
       (layout
        (config-page (when reset "cleared"))))
       
  (POST "/configs" req
    (do (reset-with-config (-> req :params (select-keys [:device-id :broadcast-address :port])))        
        (layout
         (config-page "updated")))))
