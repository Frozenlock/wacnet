(ns wacnet.views.vigilia-routes
  (:require [compojure.core :refer [defroutes GET POST context]]
            [wacnet.views.common :refer [layout with-sidebar]]
            [wacnet.vigilia.logger.timed :as timed]
            [wacnet.vigilia.logger.scan :as scan]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [ring.util.response :as resp]
            [clojure.string :as string]
            [wacnet.util.helperfn :as helpfn]
            [clj-http.client :as client]
            [noir.session :as session]))

;;; First we should validate if the user can see the remote servers
;;; (is he even on the Internet?)


(defn connection-status []
  [:div.well
    [:strong "Connection status: "] 
    (if (scan/can-connect?) 
      [:span.text-success [:strong "Good"]]
      [:span [:strong.text-danger {:style "font-size: 1.5em"} "Can't connect!"]
       [:div "We can't connect to the remote servers."]
       [:div "Does this machine have an Internet access?"]
       [:div "Refresh this page to retest the connection."]])])



(defn credentials-validation
  "Test the credentials to see if they correctly give access to the
  Vigilia account." []
  [:div.well
   [:strong "Credentials: "
   (if (scan/credentials-valid?)
     [:span.text-success "Valid; access to Vigilia account"]
     [:div.bg-danger
      [:span.text-danger 
       [:p "Error! We could not connect to your account. "]
       [:p "Check your connection status first."]
       [:p "If everything is fine, check your "
        "credentials in the configurations below."]]])]])


(defn connection-and-access []
  (list
   (connection-status)
   (credentials-validation)))


;;;;;;;;;;;;;;;;;;;;;;


(defn link-to-project []
  (when-let [configs (scan/get-configs-only)]
    [:div.text-center
     [:span {:style "padding: 5px; border: 1px solid #cecece; border-radius:5px;"}
      "See the logged data at your "
      [:a {:href (str "https://hvac.io/vigilia/v/" (:project-id configs))
           :target "_blank"}
       "project page "[:i.fa.fa-external-link]]]]))



;;; ===========================================

;;; And now some of the configs

(defn credentials-form [logger-config]
  [:div.row
   [:div.col-sm-6
    [:div.form-group
     [:label "Project-id"]
     (hf/text-field {:class "form-control"}
                    :project-id (:project-id logger-config))]]
   [:div.col-sm-6
    [:div.form-group
     [:label "Logging Password"]
     (hf/text-field {:class "form-control"}
                    :logger-password (:logger-password logger-config))
     [:p.text-info 
      "You can configure it in your Vigilia configuration page."]]]])


(defn interval-form [logger-config]  
  [:div.form-group
   [:label "Log interval (minutes)"]
   (hf/text-field {:placeholder 10 :class "form-control"}
                  :time-interval (:time-interval logger-config))
   [:p.text-info (str "This is the time interval at which the BACnet network is scanned. "
                      "Smaller interval means more precision, but also more storage usage. "
                      "10 minutes is a great interval to see most HVAC behaviors.")]])

(defn range-form [logger-config]
  [:div.form-group
   [:label "Device IDs range"]
   [:div.row
    [:div.col-md-6 
     [:label "Min"]
     (hf/text-field {:class "form-control"}
                    :min-range (:min-range logger-config))]
    [:div.col-md-6 
     [:label "Max"]
     (hf/text-field {:class "form-control"}
                    :max-range (:max-range logger-config))]]
   [:p.text-info (str "Useful if you want to log only a part of your network. For example, "
                      "if you only want to log the devices with IDs below 10000, you would set "
                      "a MAX of 9999 and a MIN of 0.")]])

(defn id-filter-form [logger-config]
  [:div.form-group
   [:h4 "Filter by device ID"]
   [:label (str "Keep" " (ex: 10000, 10200, 22000)")]
   (hf/text-area {:class "form-control"}
                 :id-to-keep (string/join ", " (:id-to-keep logger-config)))
   [:label (str "Remove" " (ex: 10000, 10200, 22000)")]
   (hf/text-area {:class "form-control"}
                 :id-to-remove (string/join ", " (:id-to-remove logger-config)))
   [:p.text-info (str "Keep, or remove devices by their ID.")]])


(defn criteria-filter-form [logger-config]
  [:div.form-group
   [:label "Filter by device properties"]
   (hf/text-area {:placeholder (str {:property "value"} {:property "value" :property2 "value"})
                  :class "form-control"}
                 :criteria-coll (clojure.string/join ", " (:criteria-coll logger-config)))
   [:p.text-info (str "Use this if you want to discard some devices based on"
                      " their device properties.")]
   [:p.text-info (str "If a device matches all the properties from a single criteria map {}, "
                      "it's discarded. Let's say we want to discard every devices with"
                      " the model name \"BACstat\", with the object name which partly matches"
                      " \"room\". This is what this filter would look like:")]
   [:pre.pre-scrollable (str {:model-name "BACstat", :object-name #"(?i)room"})]
   [:p.text-info "Most used properties:"
    [:ul (for [p [:vendor-identifier :description :device-type
                  :vendor-name :object-name :model-name]]
           [:li (str p)])]]])


(defn advanced-configs-panel [configs]
  [:div.panel.panel-default
   [:div.panel-heading.panel-toggle {:data-toggle "collapse" :data-target "#advanded-configs"
                                     :style "cursor: pointer;"}
    [:h4.panel-title.text-center "Advanced logging configurations " [:i.fa.fa-chevron-down]]]
   [:div.panel-collapse.collapse {:id "advanded-configs"}
    [:div.panel-body
     [:div.row
      [:div.col-sm-6
       (interval-form configs)]
      [:div.col-sm-6
       (range-form configs)]]
     [:div.row
      [:div.col-sm-6
       (id-filter-form configs)]
      [:div.col-sm-6
       (criteria-filter-form configs)]]]]])


;;; ===========================================


(defn prepare-configs
  "Prepare a raw config map (from a webform) to be stored into the
  database."[config-map]
  (let [{:keys [logger-password min-range max-range id-to-remove
                id-to-keep time-interval criteria-coll port device-id
                project-id]}
        config-map]
    {:logger-password (if (empty? logger-password) nil (string/trim logger-password))
     :min-range (helpfn/parse-or-nil min-range)
     :max-range (helpfn/parse-or-nil max-range)
     :id-to-remove (seq (map helpfn/parse-or-nil (re-seq #"\d+" id-to-remove)))
     :id-to-keep (seq (map helpfn/parse-or-nil (re-seq #"\d+" id-to-keep)))
     :time-interval (helpfn/parse-float-or-nil time-interval)
     :criteria-coll (seq (helpfn/safe-read (str "["criteria-coll"]")))
     :project-id (when-not (empty? project-id) (string/trim project-id))}))


                              

(defn ctrl-btn [param content]
  (he/link-to (hiccup.util/url "/vigilia" {param true}) content))

(defn vigilia-controls []
  (when (scan/get-configs-only)
    [:div.row
     [:h3 "Vigilia Logging Controls"]
     [:div.col-md-4
      [:div.btn-toolbar
       (he/link-to "/vigilia/configs/stop-vigilia"
                   [:div.btn.btn-danger {:style "margin-left: 1em;"} "Stop"])
       (he/link-to "/vigilia/configs/start-vigilia"
                   [:div.btn.btn-success {:style "margin-left: 1em;"} "Start"])]]
     [:div.col-sm-6
      [:p "Wacnet will start the Vigilia logging automatically on startup if a "
       "Vigilia configuration file is found."]]]))

(defn vigilia-status []
  (when (scan/get-configs-only)
    [:div
      [:div.row
       [:h3 "Vigilia Logging Status"]
       [:div.col-sm-2
        (let [status @timed/logging-state
              class (get {"Mapping network" "label-warning"
                          "Logging" "label-success"
                          "Stopped" "label-danger"} status "")]
          [:span {:class (str "label " class) :style "font-size:1.3em;"} status])]
       [:div.col-sm-2
        (he/link-to "/vigilia/configs"
                    [:div.btn.btn-default "Refresh"])]
       [:div.col-sm-6
        [:p "Initial mapping of the network can take longer (minutes) if you have a large network. "
         "Also note that devices can be discovered after the initial mapping and will be scanned as required." ]]]
      [:div.row
       [:div.col-sm-2
        [:p "Local logs: "]]
       [:div.col-sm-2
        (str (count (scan/find-unsent-logs)))]
       [:div.col-sm-8
        [:p "Local logs are logs that weren't sent back (yet) to the remote servers. "
         "Perhaps a connection problem? If they start to accumulate, "
         "you should investigate your Internet access."]]]
      [:div.row
       [:div.col-sm-2
        [:p "Last scan duration: "]]
       [:div.col-sm-2 (format "%.1f" (double (/ (or @scan/last-scan-duration 0) 1000 60))) " min"]
       [:div.col-sm-8
        [:p "Please don't set a scanning interval smaller than this value. "
         "The time taken to do a scan is determined by your network. MS/TP devices WILL "
         "take longer than IP or Ethernet devices."]]]
      (when (not= @timed/logging-state "Stopped")
        [:div.row
         [:div.col-sm-6.col-sm-offset-3.well
          [:h4 "Device IDs to scan" [:small " Can be influenced by the advanced configurations"]]
          (if (= @timed/logging-state "Mapping network")
            [:p "Still mapping the network..."]
            [:p (string/join ", "(sort (scan/find-id-to-scan)))"."])]])]))


(defn config-to-bootstrap-form [config]
  (into [:p]
        (for [m config]
          (let [n (name (key m))]
            [:div.form-group
             [:label.control-label.col-sm-4 n]
             [:div.col-sm-8
              (let [v (val m)]
                (hf/text-field {:class "form-control"} n v))]]))))


(defn map-to-bootstrap [map]
  (for [m map]
    [:div.row {:style "background:rgba(0,0,0,0.05);margin:2px;"}
     [:div.col-sm-4
      [:p {:style "margin-left:1em;"}(name (key m))]]
     [:div.col-sm-8
      (let [v (val m)]
        (if (map? v)
          (map-to-bootstrap v)
          [:p {:style "margin-left:1em;"}
           (if (keyword? v) (name v)
               (str v))]))]]))


(defn configurations []
  (let [configs (scan/get-configs-only)]
    (list
     [:div.row
      [:div.col-sm-12
       [:h3 "Configurations"]
       (hf/form-to [:post "/vigilia/configs"]
                   (hf/with-group :config
                     [:div
                      [:div.panel.panel-default
                       [:div.panel-heading [:h3.panel-title.text-center "Credentials"]]
                       [:div.panel-body (credentials-form configs)]]
                      (advanced-configs-panel configs)])
                     

                     [:div.text-center {:style "margin-top: 1em;"}
                      (hf/submit-button {:class "btn btn-primary"}
                                        "Update the configurations")])]]
     (when configs
       [:div.row
        [:div.col-sm-12
         [:h4 "Current configurations"]
         (map-to-bootstrap configs)]])
     (when configs
       [:div.row {:style "margin-top: 1em;"}
        [:div.col-sm-12.text-center
         [:div (he/link-to "/vigilia/configs/delete-configs"
                           [:div.btn.btn-warning "Delete configs"])]
         [:span {:style "margin-left: 1em;"}
          "(And disable automatic logging when Wacnet starts.)"]]]))))




(defn vigilia-page
  [{:keys [config] :as args}]
  (layout
   [:div ;; {:style (str "background-image:url(/img/vigilia.png); background-repeat: no-repeat;"
         ;;              "min-height: 20em;")}
    [:div.container
     [:div.row
      [:div.col-sm-6.col-sm-offset-3.text-center
       [:div.hero-unit [:h1 "Vigilia" [:small " Quick and Easy Data Logging"]]
        [:div [:h3 "Ever found yourself wishing for more data while troubleshooting a system?"]
         [:h4 "Never again will you say "
          [:i "\"Oh gee, if only I had created a trend log for this object...\""]]]]]]
     [:hr]
     (connection-and-access)
     (link-to-project)
     [:hr]
     (configurations)
     [:hr]
     (vigilia-status)
     [:hr]
     (vigilia-controls)
     [:div {:style "min-height: 10em;"}]]]))

(defroutes vigilia-routes
  (context "/vigilia" []
    (GET "/configs" [:as args]
      (vigilia-page args))
    (GET "/configs/start-vigilia" []
      (timed/start-logging)
      (resp/redirect "/vigilia/configs"))
    (GET "/configs/stop-vigilia" []
      (timed/stop-logging)
      (resp/redirect "/vigilia/configs"))
    (GET "/configs/delete-configs" []
      (scan/delete-configs)
      (resp/redirect "/vigilia/configs"))
    (POST "/configs" [config :as args]
      (when config
        (session/put! :msg (str "Configurations updated. "
                                (when (timed/is-logging?) "Vigilia logging stopped.")))
        (timed/stop-logging)
        (-> (prepare-configs config)
            (scan/save-configs)))
      (resp/redirect-after-post "/vigilia/configs"))))
