(ns wacnet.views.services
  (:require [compojure.core :refer [defroutes GET POST]]
            [wacnet.views.common :refer [layout with-sidebar]]
            [logger.timed :as timed]
            [logger.scan :as scan]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [noir.response :as resp]
            [clojure.string :as string]))

(defn ctrl-btn [param content]
  (he/link-to (hiccup.util/url "/services/logger" {param true}) content))

(defn logger-controls []
  (when (scan/get-configs)
    [:div.row-fluid
     [:h3 "Logger controls"]
     [:div.span4
      [:div.btn-toolbar
       (he/link-to "/services/logger/stop-logger"
                   [:div.btn.btn-danger {:style "margin-left: 1em;"} "Stop"])
       (he/link-to "/services/logger/start-logger"
                   [:div.btn.btn-success {:style "margin-left: 1em;"} "Start"])]]
     [:div.span6
      [:p "Starting the logger will reinitialize the local device with the logger configurations."]
      [:p "Wacnet will start the logger automatically on startup if a "
       "logger configuration file is found."]]]))

(defn logger-status []
  (when (scan/get-configs)
    (list
     [:div.row-fluid
      [:h3 "Logger status"]
      [:div.span2
       (let [status @logger.timed/logging-state
             class (get {"Mapping network" "badge-warning"
                         "Logging" "badge-success"
                         "Stopped" "badge-important"} status "")]
         [:span {:class (str "badge " class)} status])]
      [:div.span2
       (he/link-to "/services/logger"
                   [:div.btn "Refresh"])]
      [:div.span6
       [:p "While mapping the network, we wait at least 30s so that all "
        "remote devices can say: "
        [:p [:i "\"Hey, I'm here!\""]]]]]
     [:div.row-fluid
      [:div.span2
       [:p "Local logs: "]]
      [:div.span2
       (str (count (scan/find-unsent-logs)))]
      [:div.span8
       [:p "Local logs are logs that weren't sent back (yet) to the remote servers. "
       "Perhaps a connection problem? If they start to accumulate, "
       "you should investigate your Internet access."]]]
     [:div.row-fluid
      [:div.span2
       [:p "Last scan duration: "]]
      [:div.span2 (format "%.2f" (double (/ (or @scan/last-scan-duration 0) 1000))) "s"]
      [:div.span8
       [:p "Please don't set a scanning interval smaller than this value. "
       "The time taken to do a scan is determined by your network. An MS/TP network WILL "
       "take longer than an IP or Ethernet network."]]])))


(defn config-to-bootstrap-form [config]
  (hf/with-group :config
    (into [:p]
          (for [m config]
            (let [n (name (key m))]
              [:div.row-fluid
               [:div.span2
                [:p {:style "margin-left:1em;"} n]]
               [:div.span10
                (let [v (val m)]
                  [:p {:style "margin-left:1em;"}
                   (hf/text-field n v)])]])))))

(defn map-to-bootstrap [config]
  (hf/with-group :config
    (into [:p]
          (for [m config]
            (let [n (name (key m))]
              [:div.row-fluid
               [:div.span2
                [:p {:style "margin-left:1em;"} n]]
               [:div.span10
                (let [v (val m)]
                  [:p {:style "margin-left:1em;"}
                   v])]])))))

(defn configurations []
  (let [config (scan/get-configs)
        credential (merge {:project-id "" :logger-password ""}
                          (select-keys config [:project-id :logger-password]))]
    (list
     [:div.row-fluid
      [:div.span12
       [:h3 "Configurations"]
       [:p "The logger configurations are fetched directly from your "
        (he/link-to "https://bacnethelp.com/my-projects" "project configuration")"."
        "The only thing you need here is the " [:code "project-id"]" and the "[:code "logger-password"]":"]
       (hf/form-to [:post "/services/logger"]
                   [:p (config-to-bootstrap-form credential)]
                   (hf/submit-button {:class "btn btn-primary"}
                                     "Retrieve the configurations!"))]]
     (when config
       [:div.row-fluid
        [:div.span12
         [:h4 "Current configurations"]
         (let [config (dissoc config :logger-password :project-id)]
           (if (empty? config)
             [:p [:span.badge.badge-important "Empty!"]
              "You should double check the "[:code "project-id"]
              " and the "[:code "logger-password"]"."]
             (map-to-bootstrap config)))]])
     (when (and config (not= @logger.timed/logging-state "Stopped"))
       [:div.row-fluid
        [:div.span12
         [:h4 "Device IDs to scan"]
         (if (= @logger.timed/logging-state "Mapping network")
           [:p "Still mapping the network..."]
           [:p (string/join ", "(sort (scan/find-id-to-scan)))"."])]])
     (when config
       [:div.row-fluid
        [:hr]
        [:div.span12
         (he/link-to "/services/logger/delete-configs"
                     [:div.btn.btn-warning "Delete configs"])
         [:span {:style "margin-left: 1em;"}
          "(And disable automatic logging when Wacnet starts.)"]]]))))

(defn logger-page
  [{:keys [config] :as args}]
  (layout
   [:div {:style "background-image:url(/img/logger.png); background-repeat: no-repeat;"}
    [:div.container
     [:div.hero-unit [:h1 "Quick and easy data logging"]
      
      [:div [:h3 "Ever found yourself wishing for more data while troubleshooting a system?"]
       
       [:p "Never again will you say "[:i "\"Oh gee, if only I had created a trend log for this object...\""]]]]
     (logger-status)
     [:hr]
     (logger-controls)
     [:hr]
     (configurations)]]))

(defroutes logger-routes
  (GET "/services/logger" [:as args]
       (logger-page args))
  (GET "/services/logger/start-logger" []
       (timed/start-logging)
       (resp/redirect "/services/logger"))
  (GET "/services/logger/stop-logger" []
       (timed/stop-logging)
       (resp/redirect "/services/logger"))
  (GET "/services/logger/delete-configs" []
       (scan/delete-configs)
       (resp/redirect "/services/logger"))
  (POST "/services/logger" [config :as args]
        (when config
          (-> (into {}
                    (for [[k v] config]
                      [k (clojure.string/trim v)]))
              str
              scan/save-configs)
          (scan/update-configs))
        (logger-page args)))