(ns wacnet.views.vigilia-routes
  (:require [compojure.core :refer [defroutes GET POST context]]
            [wacnet.views.common :refer [layout with-sidebar]]
            [vigilia-logger.timed :as timed]
            [vigilia-logger.scan :as scan]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [ring.util.response :as resp]
            [clojure.string :as string]
            [wacnet.util.helperfn :as helpfn]
            [noir.session :as session]))

;;; First we should validate if the user can see the remote servers
;;; (is he even on the Internet?)


(defn connection-status [logger-config]
  (let [api-root (scan/get-api-root)]
    [:div.well
     [:h3 "Step 1: Connect to a Vigilia server"]
     [:strong "Vigilia server connection : "] 
     (if (scan/can-connect? api-root)
       [:span.text-success [:strong "Success"]
        [:div "Connected to "(he/link-to api-root api-root)]]
       [:span [:strong.text-danger {:style "font-size: 1.5em"} "Can't connect!"]
        [:div "We can't connect to the Vigilia server at "
         [:strong (he/link-to api-root api-root)]]
        [:div "Does this machine have a network access?"]])
       [:hr]
       [:div.form-group
        [:label "Vigilia API URL (leave empty for default)"]
        (hf/text-field {:class "form-control"}
                       :api-root (:api-root logger-config ))]
       (hf/submit-button {:class "btn btn-primary"}
                         "Update")]))




(defn credentials-form [logger-config]
  [:div.row
   [:div.col-sm-6
    [:div.form-group
     [:label "Project-id"]
     (hf/text-field {:class "form-control"}
                    :project-id (:project-id logger-config))]]
   [:div.col-sm-6
    [:div.form-group
     [:label "Logger key"]
     (hf/text-field {:class "form-control"}
                    :logger-key (:logger-key logger-config))
     [:p.text-info 
      "You can configure it in your Vigilia configuration page."]]]])


(defn credentials-validation
  "Test the credentials to see if they correctly give access to the
  Vigilia account." [logger-config]
  [:div.well
   [:h3 "Step 2: Project credentials"]
   [:strong "Credentials : "
   (if (scan/credentials-valid?)
     [:span.text-success "Valid; access to Vigilia account"]
     [:strong.text-danger {:style "font-size: 1.5em"} "No access"])]
   (credentials-form logger-config)
   (hf/submit-button {:class "btn btn-primary"}
                     "Update")])


(defn connection-and-access []
  (let [configs (scan/get-logger-configs)]
    [:div 
     ;[:p [:span.label.label-info scan/logger-version]]
     (connection-status configs)
     (credentials-validation configs)]))


;;;;;;;;;;;;;;;;;;;;;;


(defn link-to-project []
  (when-let [configs (scan/get-logger-configs)]
    [:div.text-center
     [:span {:style "padding: 5px; border: 1px solid #cecece; border-radius:5px;"}
      "See the logged data at your "
      [:a {:href (str "https://vigilia.hvac.io/v/" (:project-id configs))
           :target "_blank"}
       "project page "[:i.fa.fa-external-link]]]]))



;;; ===========================================

;;; And now some of the configs

(defn logger-id-form [logger-config]
  (let [l-id (or (:logger-id logger-config)
                 (scan/new-logger-id!))]
    [:div.form-group
     [:label "Logger ID (ex: logger-first-floor)"]
     (hf/text-field {:class "form-control"}
                    :logger-id l-id)
     [:p.text-info 
      "When recording a network using multiple loggers, the logger ID "
      "can help you with troubleshooting."]]))

(defn interval-form [logger-config]  
  [:div.form-group
   [:label "Log interval (minutes)"]
   (hf/text-field {:class "form-control"}
                  :time-interval (or (:time-interval logger-config) 10))
   [:p.text-info (str "This is the time interval at which the BACnet network is scanned. "
                      "Smaller interval means more precision, but also more network utilization. "
                      "10 minutes is a great interval to see most HVAC behaviors. "
                      "(Minimum interval is 5 minutes.) Note that this is the minimum interval. "
                      "It might take longer if you have a large or slow network.")]])

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


(defn object-delay-form [logger-config]
  (let [current-delay (or (:object-delay logger-config) 0)]
    [:div.form-group
     [:label "Read objects delay (milliseconds) " 
      [:output.label.label-info 
       {:id "rangevalue" :style "display: inline-block"} current-delay]]
     [:input {:type :range :id :object-delay :value current-delay 
              :name (reduce #(str %1 "[" %2 "]")
                            (conj hf/*group* (name :object-delay)))
              :onchange "rangevalue.value=value" :max 1000 :min 0 :step 50}]
     [:p.text-info (str "Delay between each object scan of a device. This increases the total scan time. "
                        "Use this if the scans cause you to lose connection with some devices.")]]))

(defn advanced-configs-panel [configs]
  [:div.panel.panel-default
   [:div.panel-heading.panel-toggle {:data-toggle "collapse" :data-target "#advanded-configs"
                                     :style "cursor: pointer;"}
    [:h4.panel-title.text-center "Advanced configs " [:i.fa.fa-chevron-down]]]
   [:div.panel-collapse.collapse {:id "advanded-configs"}
    [:div.panel-body
     [:div.row
      [:div.col-sm-6
       (logger-id-form configs)
       (interval-form configs)]
      [:div.col-sm-6
       (range-form configs)]]
     [:div.row
      [:div.col-sm-6
       (id-filter-form configs)
       (object-delay-form configs)]
      [:div.col-sm-6
       (criteria-filter-form configs)]]]]])


;;; ===========================================


(defn prepare-configs
  "Prepare a raw config map (from a webform) to be stored into the
  config file."[config-map]
  (let [{:keys [logger-key min-range max-range id-to-remove
                id-to-keep time-interval criteria-coll port device-id
                project-id object-delay api-root logger-id]}
        config-map]
    {:logger-key (if (empty? logger-key) nil (string/trim logger-key))
     :min-range (helpfn/parse-or-nil min-range)
     :max-range (helpfn/parse-or-nil max-range)
     :id-to-remove (seq (map helpfn/parse-or-nil (re-seq #"\d+" id-to-remove)))
     :id-to-keep (seq (map helpfn/parse-or-nil (re-seq #"\d+" id-to-keep)))
     :time-interval (when-let [t-i (helpfn/parse-float-or-nil time-interval)]
                      (max t-i 5))
     :criteria-coll (seq (helpfn/safe-read (str "["criteria-coll"]")))
     :project-id (when-not (empty? project-id) (string/trim project-id))
     :object-delay (when-let [o-d (helpfn/parse-or-nil object-delay)]
                     (when (> o-d 0) o-d))
     :api-root (let [r (some-> api-root string/trim)]
                 (when-not (empty? r) r))
     :logger-id (let [r (some-> logger-id string/trim)]
                  (when-not (empty? r) r))}))

(defn logging-status []
  [:div 
   [:h3 "Status : "
   [:span.small
    (let [status @timed/logging-state
          class (get {"Mapping network" "label-warning"
                      "Logging" "label-success"
                      "Stopped" "label-danger"} status "")]
      [:span {:class (str "label " class) :style "font-size:1.3em;"} status])]
    " "
    (he/link-to "/vigilia/configs"
                [:div.btn.btn-default.btn-sm 
                 [:span [:i.fa.fa-refresh] " Refresh"]])]])
                              

(defn ctrl-btn [param content]
  (he/link-to (hiccup.util/url "/vigilia" {param true}) content))

(defn vigilia-controls []
  (let [status @timed/logging-state
        btn-disabled? (not (:project-id (scan/get-logger-configs)))]
    [:div.row
     [:div.col-md-4
      [:h4 "Logging Controls"]
      [:div.btn-toolbar
       (he/link-to "/vigilia/configs/stop-vigilia"
                   [:div.btn.btn-danger {:style "margin-left: 1em;"
                                         :disabled (= "Stopped" status)}
                    "Stop"])
       (he/link-to "/vigilia/configs/start-vigilia"
                   [:div.btn.btn-success {:style "margin-left: 1em;"
                                          :disabled 
                                          (if (= "Stopped" status)
                                            btn-disabled?
                                            true)}
                    "Start"])]]
     [:div.col-sm-6
      [:p "Wacnet will start the Vigilia logging automatically on startup if a "
       "Vigilia configuration file is found."]]]))

(defn vigilia-status []
  (when (scan/get-logger-configs)
    [:div
     (logging-status)
     [:div.panel.panel-default
      [:div.panel-heading.panel-toggle {:data-toggle "collapse" :data-target "#logging-status"
                                        :style "cursor: pointer;"}
       [:h4.panel-title.text-center "Details " [:i.fa.fa-chevron-down]]]
      [:div.panel-collapse.collapse {:id "logging-status"}
       [:div.panel-body
        [:div.row
         [:div.col-sm-6.col-sm-offset-3.text-center
          [:p.text-info "Initial mapping of the network can take some time (minutes)"
           " if you have a large network. "
           "Also note that devices can be discovered after the initial mapping and will be scanned as required." ]]]
        [:hr]
        [:div.row
         [:div.col-sm-2
          [:p "Local logs: "]]
         [:div.col-sm-2
          (str (count (scan/find-unsent-logs)))]
         [:div.col-sm-8
          [:p "Local logs are logs that weren't sent back (yet) to the remote servers. "
           "Perhaps a connection problem? If they start to accumulate, "
           "you should investigate your network access."]]]
        [:div.row
         [:div.col-sm-2
          [:p "Last scan duration: "]]
         [:div.col-sm-2 (format "%.3f" (double (/ (or @scan/last-scan-duration 0) 1000 60))) " min"]
         [:div.col-sm-8
          [:p "Don't set a scanning interval smaller than this value. "
           "The time taken to do a scan is determined by your network. MS/TP devices WILL "
           "take longer than IP or Ethernet devices."]]]
        (when (not= @timed/logging-state "Stopped")
          [:div.row
           [:div.col-sm-6.col-sm-offset-3.well
            [:h4 "Device IDs to scan" [:small " Can be influenced by the advanced configurations"]]
            (if (= @timed/logging-state "Mapping network")
              [:p "Still mapping the network..."]
              [:p (string/join ", "(sort (scan/find-id-to-scan)))"."])]])
        [:hr]
        (vigilia-controls)]]]]))


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
  (let [configs (scan/get-logger-configs)]
    (list
     [:div.well
      [:h3 "Step 3: Advanced configurations (optional)"]
      (advanced-configs-panel configs)
      (hf/submit-button {:class "btn btn-primary"}
                        "Update")]
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
       [:div.hero-unit
        [:h1
         [:div [:img.shadow {:src "/img/Vigilia-logo-name.svg" :alt "Vigilia" :style "max-height: 3em;"}]]
         [:small "HVAC Logging and Remote Monitoring"]]
        [:div [:h3 "Ever found yourself wishing for more data while troubleshooting a system?"]
         [:h4 "Never again will you say "
          [:i "\"Oh gee, if only I had created a trend log for this object...\""]]]
        [:div (he/link-to "https://hvac.io/services/vigilia"
                          "Learn more")]]]]
     [:hr]
     (vigilia-status)

     [:hr]
     (hf/form-to [:post "/vigilia/configs"]
                 (hf/with-group :config
                                        ;(link-to-project)
                   (connection-and-access)
                   
                   [:hr]
                   (configurations)
                   ))
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
      (scan/delete-logger-configs!)
      (resp/redirect "/vigilia/configs"))
    (POST "/configs" [config :as args]
      (when config
        (session/put! :msg (str "Configurations updated. "
                                (when (timed/is-logging?) "We stopped the logging as a precaution.")))
        (timed/stop-logging)
        (-> (prepare-configs config)
            (scan/save-logger-configs!)))
      (resp/redirect-after-post "/vigilia/configs"))))
