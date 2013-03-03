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
  (hf/with-group :config
    (into [:p]
          (for [m config]
            (let [n (name (key m))]
              [:div.row-fluid {:style "border: 1px solid #cecece; border-radius:5px;"}
               [:div.span4
                [:p {:style "margin-left:1em;"} n]]
               [:div.span8
                (let [v (val m)]
                  [:p {:style "margin-left:1em;"}
                   (hf/text-field n v)])]])))))

(defn make-configs-forms []
  (-> (ld/local-device-backup)
      (dissoc :objects)
      config-to-bootstrap))

(defn config-page [& msg]
  (with-sidebar {:header "Local interfaces"
                 :sidebar (->> (net/interfaces-and-ips)
                               (map #(vector :li (:interface %) ": " (clojure.string/join ", " (:ips %)))))
                 :body
                 [:div.container
                  [:div.hero-unit
                   [:h1 "Local device configurations"]
                   (when msg [:span.label.label-success (first msg)])
                   (hf/form-to [:post "/configs"]
                               [:p (make-configs-forms)]
                               [:div.btn-toolbar
                                (hf/submit-button {:class "btn btn-primary" :style "margin-right: 1em;"}
                                                  "Validate and reinitialize the local device!")
                                (he/link-to (hiccup.util/url "/configs" {:reset true})
                                            [:div.btn.btn-danger "Reset default"])])]
                  (when (save/get-configs)
                    [:div.badge.badge-warning "You are using a saved config file."])]}))
  
(defroutes configs-routes
  (GET "/configs" [reset]
       (when reset (reset-default))
       (layout
        (config-page (when reset "cleared"))))
       
  (POST "/configs" [config]
        (reset-with-config config)
        (layout
         (config-page "updated"))))
