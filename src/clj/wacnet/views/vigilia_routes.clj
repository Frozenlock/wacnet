(ns wacnet.views.vigilia-routes
  (:require [compojure.core :refer [defroutes GET POST context]]
            [wacnet.views.common :refer [layout with-sidebar]]
            [wacnet.vigilia.logger.timed :as timed]
            [wacnet.vigilia.logger.scan :as scan]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [noir.response :as resp]
            [clojure.string :as string]))

(defn ctrl-btn [param content]
  (he/link-to (hiccup.util/url "/vigilia" {param true}) content))

(defn vigilia-controls []
  (when (scan/get-configs-only)
    [:div.row
     [:h3 "Logger controls"]
     [:div.col-md-4
      [:div.btn-toolbar
       (he/link-to "/vigilia/configs/stop-vigilia"
                   [:div.btn.btn-danger {:style "margin-left: 1em;"} "Stop"])
       (he/link-to "/vigilia/configs/start-vigilia"
                   [:div.btn.btn-success {:style "margin-left: 1em;"} "Start"])]]
     [:div.col-md-6
      [:p "Starting Vigilia will reinitialize the local device with the Vigilia configurations."]
      [:p "Wacnet will start the Vigilia automatically on startup if a "
       "Vigilia configuration file is found."]]
     [:hr]]))

(defn vigilia-status []
  (when-let [configs (scan/get-configs-only)]
    (list
     [:div.row
      [:div.col-md-3]
      [:div.col-md-6
       [:span {:style "padding: 5px; border: 1px solid #cecece; border-radius:5px;"}
        "See the logged data at your "(he/link-to (str "https://hvac.io/vigilia/v/" 
                                                       (:project-id configs))
                                                  "project page")]]
      [:div.col-md-3]]
                                               
     [:div.row
      [:h3 "Logger status"]
      [:div.col-md-2
       (let [status @timed/logging-state
             class (get {"Mapping network" "badge-warning"
                         "Logging" "badge-success"
                         "Stopped" "badge-important"} status "")]
         [:span {:class (str "badge " class)} status])]
      [:div.col-md-2
       (he/link-to "/vigilia/configs"
                   [:div.btn "Refresh"])]
      [:div.col-md-6
       [:p "Initial mapping of the network can take longer (minutes) if you have a large network. "
        "Also note that devices can be discovered after the initial mapping and will be scanned as required." ]]]
     [:div.row
      [:div.col-md-2
       [:p "Local logs: "]]
      [:div.col-md-2
       (str (count (scan/find-unsent-logs)))]
      [:div.col-md-8
       [:p "Local logs are logs that weren't sent back (yet) to the remote servers. "
       "Perhaps a connection problem? If they start to accumulate, "
       "you should investigate your Internet access."]]]
     [:div.row
      [:div.col-md-2
       [:p "Last scan duration: "]]
      [:div.col-md-2 (format "%.1f" (double (/ (or @scan/last-scan-duration 0) 1000 60))) " min"]
      [:div.col-md-8
       [:p "Please don't set a scanning interval smaller than this value. "
        "The time taken to do a scan is determined by your network. MS/TP networks WILL "
        "take longer than an IP or Ethernet network."]]]
     [:hr])))


(defn config-to-bootstrap-form [config]
  (hf/with-group :config
    (into [:p]
          (for [m config]
            (let [n (name (key m))]
              [:div.form-group
               [:label.control-label.col-md-4 n]
               [:div.col-md-8
                (let [v (val m)]
                  [:input.form-control {:type :text :name n :value v}])]])))))


              ;; [:div.row
              ;;  [:div.col-md-2
              ;;   [:p {:style "margin-left:1em;"} n]]
              ;;  [:div.col-md-10
              ;;   (let [v (val m)]
              ;;     [:p {:style "margin-left:1em;"}
              ;;      (hf/text-field n v)])]])))))

(defn map-to-bootstrap [map]
  (for [m map]
    [:div.row {:style "border: 1px solid #cecece; border-radius:5px;"}
     [:div.col-md-4
      [:p {:style "margin-left:1em;"}(name (key m))]]
     [:div.col-md-8
      (let [v (val m)]
        (if (map? v)
          (map-to-bootstrap v)
          [:p {:style "margin-left:1em;"}
           (if (keyword? v) (name v)
               (str v))]))]]))


(defn configurations []
  (let [config (scan/get-configs-only)
        credential (merge {:project-id "" :vigilia-password ""}
                          (select-keys config [:project-id :vigilia-password]))]
    (list
     [:div.row
      [:div.col-md-12
       [:h3 "Configurations"]
       [:p "The vigilia configurations are fetched directly from your "
        (he/link-to "https://hvac.io/projects" "project configuration")"."
        "The only thing you need here is the " [:code "project-id"]" and the "[:code "vigilia-password"]":"]
       (hf/form-to [:post "/vigilia/configs"]
                   [:p (config-to-bootstrap-form credential)]
                   [:div.text-center {:style "margin-top: 1em;"}
                    (hf/submit-button {:class "btn btn-primary"}
                                      "Retrieve the configurations!")])]]
     (when config
       [:div.row
        [:div.col-md-12
         [:h4 "Current configurations"]
         (let [config (dissoc config :vigilia-password :project-id)]
           (if (empty? config)
             [:div.alert.alert-danger
              [:p [:span.badge.badge-important "Empty!"]
               "You should double check the "[:code "project-id"]
               " and the "[:code "vigilia-password"]"."]]
             (map-to-bootstrap config)))]])
     (when (and config (not= @timed/logging-state "Stopped"))
       [:div.row
        [:div.col-md-12
         [:h4 "Device IDs to scan"]
         (if (= @timed/logging-state "Mapping network")
           [:p "Still mapping the network..."]
           [:p (string/join ", "(sort (scan/find-id-to-scan)))"."])]])
     (when config
       [:div.row
        [:hr]
        [:div.col-md-12
         (he/link-to "/vigilia/configs/delete-configs"
                     [:div.btn.btn-warning "Delete configs"])
         [:span {:style "margin-left: 1em;"}
          "(And disable automatic logging when Wacnet starts.)"]]]))))

(defn vigilia-page
  [{:keys [config] :as args}]
  (layout
   [:div {:style (str "background-image:url(/img/vigilia.png); background-repeat: no-repeat;"
                      "min-height: 20em;")}
    [:div.container
     [:div.hero-unit [:h1 "Quick and easy data logging"]
      
      [:div [:h3 "Ever found yourself wishing for more data while troubleshooting a system?"]
       
       [:p "Never again will you say "[:i "\"Oh gee, if only I had created a trend log for this object...\""]]]]
     (vigilia-status)
     (vigilia-controls)
     (configurations)]]))

(defroutes vigilia-routes
  (context "/vigilia" []
    (GET "/configs" [:as args]
      (vigilia-page args))
    (GET "/configs/start-vigilia" []
      (timed/start-logging)
      (resp/redirect "/configs"))
    (GET "/configs/stop-vigilia" []
      (timed/stop-logging)
      (resp/redirect "/configs"))
    (GET "/configs/delete-configs" []
      (scan/delete-configs)
      (resp/redirect "vigilia/configs"))
    (POST "/configs" [config :as args]
      (when config
        (-> (into {}
                  (for [[k v] config]
                    [k (clojure.string/trim v)]))
            str
            scan/save-configs)
        (scan/update-configs))
      (vigilia-page args))))
