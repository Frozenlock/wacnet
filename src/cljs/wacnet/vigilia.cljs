(ns wacnet.vigilia
  (:require [reagent.core :as r]
            [re-com.core :as re]
            [ajax.core :refer [GET POST DELETE]]
            [re-com.core :as re]
            [wacnet.templates.common :as common]
            [clojure.string :as s]
            [goog.string :as gstring]            
            [wacnet.explorer.devices :as dev]))

(def configs-api-url "/api/v1/vigilia/logger/configs")


(defn logo [alt]
  (let [height "1.5em"]
    [:img {:src "/img/Vigilia-logo-name.svg"
           :style {:height height}
           :alt alt}]))

(defn heading []
  [:div.text-center
   [:h1 [:div [logo "Vigilia"]]
    [:small "HVAC Logging and Remote Monitoring"]]
   [:h3 "Ever found yourself wishing for more data while troubleshooting a system?"]
   [:h4 "Never again will you say "
    [:i "\"Oh gee, if only I had created a trend log for this object...\""]]
   [:div [:a {:href "https://hvac.io/services/vigilia" :target "_blank"} "Learn more"]]])

;;; And now some of the configs


(defn form-group 
  ([value-atom label info]
   (form-group value-atom label info {:type :text}))
  ([value-atom label info attrs]
   [:div.form-group
    [:label label]
    [:input.form-control (merge attrs 
                                {:value @value-atom
                                 :on-change #(let [v (-> % .-target .-value)]
                                               (reset! value-atom v))})]
    [:p.text-info info]]))

(defn logger-id-form [logger-config-a]
  [form-group 
   (r/cursor logger-config-a [:logger-id]) 
   "Logger ID (ex: logger-first-floor)"
   (str "When recording a network using multiple loggers, the logger ID "
        "can help you with troubleshooting.")])

(defn interval-form [logger-config-a]
  [form-group 
   (r/cursor logger-config-a [:time-interval])
   "Log interval (minutes)"
   (str "This is the time interval at which the BACnet network is scanned. "
                      "Smaller interval means more precision, but also more network utilization. "
                      "10 minutes is a great interval to see most HVAC behaviors. "
                      "(Minimum interval is 5 minutes.) Note that this is the minimum interval. "
                      "It might take longer if you have a large or slow network.")
   {:type :number :min 5}])


(defn range-form [logger-config-a]
  (let [min-range-a (r/cursor logger-config-a [:min-range])
        max-range-a (r/cursor logger-config-a [:max-range])]
    [:div.form-group
     [:label "Device IDs range"]
     [:div.row
      [:div.col-md-6 
       [:label "Min"]
       [:input.form-control {:type :number :min 0 :value @min-range-a
                             :on-change #(reset! min-range-a (-> % .-target .-value))}]]
      [:div.col-md-6 
       [:label "Max"]
       [:input.form-control {:type :number :min 0 :value @max-range-a
                             :on-change #(reset! max-range-a (-> % .-target .-value))}]]]
     [:p.text-info (str "Useful if you want to log only a part of your network. For example, "
                        "if you only want to log the devices with IDs below 10000, you would set "
                        "a MAX of 9999 and a MIN of 0.")]]))


(defn coll-area 
  ([initial-value on-blur] (coll-area initial-value on-blur nil))
  ([initial-value on-blur placeholder]
   (let [value (r/atom "")
         init-value (atom initial-value)]
     (fn [initial-value on-blur]
       (when-not (= initial-value @init-value)
         (reset! init-value initial-value)
         (reset! value initial-value))
       [:textarea.form-control {:value @value
                                :placeholder placeholder
                                :on-change #(reset! value (-> % .-target .-value))
                                :on-blur on-blur}]))))

(defn id-filter-form [logger-config-a]
  (let [keep-a (r/cursor logger-config-a [:id-to-keep])
        remove-a (r/cursor logger-config-a [:id-to-remove])
        print-seq (fn [value] (or (s/join ", " (map str value)) ""))
        read-seq (fn [string] (map #(js/parseInt %)
                                   (-> string
                                       (s/replace "," "")
                                       (s/split #" ")
                                       (distinct)
                                       (seq))))]
    (fn [logger-config-a]
      [:div.form-group
       [:h4 "Filter by device ID"]
       [:label (str "Keep" " (ex: 10000, 10200, 22000)")]
       [coll-area (print-seq @keep-a) #(->> % .-target .-value read-seq
                                            (reset! keep-a))]
       [:label (str "Remove" " (ex: 10000, 10200, 22000)")]
       [coll-area (print-seq @remove-a) #(->> % .-target .-value read-seq
                                            (reset! remove-a))]       
       [:p.text-info (str "Keep, or remove devices by their ID.")]])))



(defn criteria-filter-form [logger-config-a]
  (let [criteria-a (r/cursor logger-config-a [:criteria-coll])
        print-seq (fn [value] (or (s/join ", " value) ""))
        read-seq (fn [string] (-> string
                                  (s/replace "," "")
                                  (s/split #" ")
                                  (distinct)
                                  (seq)))
        temp-criteria-a (r/atom (print-seq @criteria-a))]
    (fn []
      [:div.form-group
       [:label "Filter by device properties"]
       [coll-area @criteria-a #(->> % .-target .-value read-seq
                                    (reset! criteria-a))
        (str {:property "value"} {:property "value" :property2 "value"})]
       [:p.text-info (str "Use this if you want to discard some devices based on"
                          " their device properties.")]
       [:p.text-info (str "If a device matches all the properties from a single criteria map {}, "
                          "it's discarded. Let's say we want to discard every devices with"
                          " the model name \"BACstat\", with the object name which partly matches"
                          " \"room\". This is what this filter would look like:")]
       [:pre.pre-scrollable (str {:model-name "BACstat", :object-name #"(?i)room"})]
       [:div.text-info "Most used properties:"
        [:ul (for [p [:vendor-identifier :description :device-type
                      :vendor-name :object-name :model-name]]
               ^{:key p}
               [:li (str p)])]]])))


(defn object-delay-form [logger-config-a]
  (let [object-delay-a (r/cursor logger-config-a [:object-delay])]
    [:div.form-group
     [:label "Read objects delay (milliseconds) " 
      [:span.label.label-info (or @object-delay-a 0)]]
     [:input {:type :range
              :value (or @object-delay-a 0)
              :on-change #(reset! object-delay-a (-> % .-target .-value js/parseInt))
              :max 1000 :min 0 :step 50}]
     [:p.text-info (str "Delay between each object scan of a device. This increases the total scan time. "
                        "Use this if the scans cause you to lose connection with some devices.")]]))

(defn load! [url params result-a loading-a error-a]
  (reset! loading-a true)
  (reset! error-a nil)
  (GET url
      {:handler (fn [response]
                  (reset! loading-a nil)
                  (reset! result-a response))
       :response-format :transit
       :params params
       :error-handler (fn [_]
                        (reset! loading-a nil)
                        (reset! error-a true))}))


(defn save-configs! [logger-config-a success-a loading-a error-a]
  (POST configs-api-url
      {:handler (fn [response]
                  (reset! loading-a nil)
                  (reset! success-a true)
                  (reset! logger-config-a (:configs response)))
       :params (->> (for [[k v] @logger-config-a]
                      (when v [k v]))
                    (remove nil?)
                    (into {}))
       :keywords? true
       :response-format :transit
       :error-handler (fn [e]
                        (reset! loading-a nil)
                        (reset! error-a e))}))

(defn save-btn
  "A 'save' button that will send the current configurations to
  Wacnet's API. Shows a success or error message to the
  user."
  [logger-config-a disabled?]
  (let [error? (r/atom nil)
        loading? (r/atom nil)
        success? (r/atom nil)]
    (fn [logger-config-a disabled?]
      [:span
       (when @success?
         (js/setTimeout #(reset! success? nil) 3000)
         [:div.message [:div.alert.alert-success "Configs updated!"]])
       (when @error?
         (js/setTimeout #(reset! error? nil) 3000)
         [:div.message [:div.alert.alert-danger "Error : " (:status-text @error?)]])
       [:button.btn.btn-primary {:on-click #(save-configs! logger-config-a success? loading? error?)}
        "Save"]])))

(defn api-url-form [logger-config-a]
  (let [test-server-a (r/atom nil)
        loading? (r/atom nil)
        error? (r/atom nil)
        api-url-a (r/cursor logger-config-a [:api-root])
        test-server-fn (fn [] 
                         (load! "/api/v1/vigilia/logger/tests/api-root" 
                                (when-let [url @api-url-a]
                                  {:api-root url})
                                test-server-a loading? error?))]
    (r/create-class
     {:component-did-mount test-server-fn
      :reagent-render
      (fn []
        (let [api-url (:api-root @test-server-a)]
          [:div.well 
           [:h3 "Step 1 : Connect to a Vigilia server"]
           [:div {:style {:height "5em"}}
            (cond
              @loading?
              [:div [:div "Testing connection..."]
               [re/throbber]]

              @error?
              [:div.alert.alert-danger
               [:p "Uh oh... the server encountered an error. Double check the provided URL."]]

              @test-server-a
              [:div [:strong "Vigilia server connection : "]
               (if (:can-connect? @test-server-a)
                 [:span.text-success [:strong "Success"]
                  [:div "Connected to " [:a {:href api-url} api-url]]]
                 [:span [:strong.text-danger "Can't connect!"]
                  [:div "We can't connect tot the Vigilia server at " 
                   [:strong [:a {:href api-url} api-url]]]
                  [:div "Does this machine have a network access?"]])])]
           
           [form-group api-url-a "vigilia API URL (leave empty for default)" nil]
           [:button.btn.btn-default {:on-click test-server-fn} "Test URL"]
           [save-btn logger-config-a (when-not (:can-connect? @test-server-a) true)]]))})))


(defn credentials-form [logger-config-a]
  (let [test-server-a (r/atom nil)
        loading? (r/atom nil)
        error? (r/atom nil)
        test-server-fn (fn [] 
                         (load! "/api/v1/vigilia/logger/tests/credentials" 
                                (let [p-id (:project-id @logger-config-a)
                                      logger-key (:logger-key @logger-config-a)] 
                                  (when (and p-id logger-key)
                                    {:project-id p-id :logger-key logger-key}))
                                test-server-a loading? error?))]
    (r/create-class
     {:component-did-mount test-server-fn
      :reagent-render
      (fn []
        (let [{:keys [project-id logger-key]} @logger-config-a]
          [:div.well 
           [:h3 "Step 2 : Project credentials"]
           [:div {:style {:height "5em"}}
            (cond
              @loading?
              [:div [:div "Testing connection..."]
               [re/throbber]]

              @error?
              [:div.alert.alert-danger
               [:p "Uh oh... the server encountered an error. Double check the provided URL."]]

              @test-server-a
              [:div [:strong "Credentials : "]
               (if (:credentials-valid? @test-server-a)
                 [:span.text-success "Valid; access to Vigilia account"]
                 [:span [:strong.text-danger "No access"]])])]
           
           [:div.row
            [:div.col-sm-6 
             [form-group (r/cursor logger-config-a [:project-id]) "Project ID" nil]]
            [:div.col-sm-6 
             [form-group (r/cursor logger-config-a [:logger-key]) "Logger Key"
              "You can configure it in your Vigilia configuration page."]]]
           [:button.btn.btn-default {:on-click test-server-fn} "Test credentials"]
           [save-btn logger-config-a (when-not (:credentials-valid? @test-server-a) true)]]))})))



(defn get-configs! 
  ([configs-a] (get-configs! configs-a nil nil))
  ([configs-a loading-a error-a]
   (when loading-a (reset! loading-a true))
   (when error-a (reset! error-a nil))
   (GET configs-api-url
       {:handler (fn [response]
                   (when loading-a (reset! loading-a nil))
                   (reset! configs-a (:configs response)))
        :response-format :transit
        :error-handler (fn [_]
                         (when loading-a (reset! loading-a nil))
                         (when error-a (reset! error-a true)))})))

(defn clear-configs! [configs-a success-a error-a]
  (DELETE configs-api-url
      {:handler (fn [resp] 
                  (reset! success-a true) 
                  (get-configs! configs-a))
       :error-handler #(reset! error-a %)}))

(defn clear-configs-btn
  "A 'save' button that will send the current configurations to
  Wacnet's API. Shows a success or error message to the
  user."
  [logger-config-a disabled?]
  (let [error? (r/atom nil)
        loading? (r/atom nil)
        success? (r/atom nil)]
    (fn [logger-config-a disabled?]
      [:span
       (when @success?
         (js/setTimeout #(reset! success? nil) 3000)
         [:div.message [:div.alert.alert-success "Configs deleted!"]])
       (when @error?
         (js/setTimeout #(reset! error? nil) 3000)
         [:div.message [:div.alert.alert-danger "Error : " (:status-text @error?)]])
       [:button.btn.btn-danger {:on-click #(clear-configs! logger-config-a success? error?)
                                :disabled disabled?
                                :style {:margin-bottom "20px"}}
        "Delete configurations " (when @loading? [:i.fa.fa-spinner.fa-pulse])]])))


(defn explorer-form [configs-a]
  (let [show-explorer? (r/atom nil)]
    (fn [configs-a]
      [:div.form-group
       [:div [:label "Filter individual objects"]]
       [:div.text-info 
        "You can select individual objects to be recorded (assuming the devices go through the previous "
        "filters.)"]
       [:div.text-info
        "If no object is selected in a device, all of them are recorded."]
       [:div.text-right 
        [:button.btn.btn-danger {:on-click #(swap! configs-a dissoc :target-objects)
                                 :disabled (when-not (seq (:target-objects @configs-a)) true)} "Clear all"]
        [:button.btn.btn-default {:on-click #(reset! show-explorer? true)} "Open explorer"]
        ]       
       
       (when @show-explorer?
         [re/modal-panel
          :backdrop-on-click #(reset! show-explorer? nil)
          :child [re/v-box
                  :height "90vh"
                  :width "90vw"
                                        ;:width "900px"
                  :children
                  [[:div.text-right
                    [:button.btn.btn-primary {:on-click #(reset! show-explorer? nil)} "Close"]]
                   [dev/controllers-view nil {:vigilia-mode configs-a}]]]])])))



(defn advanced-configs-panel [configs-a]
  [:div.panel.panel-default
   [:div.panel-heading.panel-toggle {:data-toggle "collapse" :data-target "#advanded-configs"
                                     :style {:cursor :pointer}}
    [:h4.panel-title.text-center "Advanced configs (optional) " [:i.fa.fa-chevron-down]]]
   [:div.panel-collapse.collapse {:id "advanded-configs"}
    [:div.panel-body
     [:div.row
      [:div.col-sm-6
       [logger-id-form configs-a]
       [interval-form configs-a]]
      [:div.col-sm-6
       [range-form configs-a]]]
     [:div.row
      [:div.col-sm-6
       [id-filter-form configs-a]
       [object-delay-form configs-a]]
      [:div.col-sm-6
       [criteria-filter-form configs-a]]]
     [:div.row
      [:div.col-sm-6 [explorer-form configs-a]]]
     [:div.text-right
      [save-btn configs-a nil]]
     ;; [:div (str @configs-a)]
     ]]])


(defn configs-steps [configs-a]
  (r/create-class
   {:component-did-mount #(get-configs! configs-a)
    :reagent-render
    (fn [configs-a]
      [:div
       [:h2 {:id "Config"} "Configurations"]
       [:p.text-info {:style {:margin 10
                              :margin-left 0}} 
        "Wacnet will start the logging automatically on startup if a "
        "Vigilia configuration file is found."]
       [api-url-form configs-a]
       [credentials-form configs-a]
       [advanced-configs-panel configs-a]
       [:div.text-right 
        [clear-configs-btn configs-a (not (:project-id @configs-a))]]])}))


(defn link-to-project [root-api-url project-id]
  (let [base-vigilia-url (last (re-find #"(.*)/api" (or root-api-url
                                                        "https://vigilia.hvac.io/api")))]
    [:div {:style {:margin-top 10}} "See the logged data at your " 
     [:a {:href (str base-vigilia-url "/v/" project-id) :target "_blank"}
      "project page "[:i.fa.fa-external-link]]]))


(defn vigilia-status [configs-a]
  (let [logger-state (r/atom nil)
        loading-a (r/atom nil)
        error-a (r/atom nil)
        logger-url "/api/v1/vigilia/logger"
        update-logger-state! (fn []
                                 (reset! loading-a true)
                                 (GET logger-url
                                     {:response-format :transit
                                      :handler (fn [resp] 
                                                 (reset! loading-a nil)
                                                 (reset! logger-state resp))
                                      :error-handler (fn [resp]
                                                       (reset! loading-a nil)
                                                       (reset! error-a true)
                                                       (prn resp))}))
        send-command! (fn [bool]
                        (POST logger-url
                            {:response-format :transit
                             :params {:logging bool}
                             :handler #(reset! logger-state %)}))]
    (r/create-class
     {:display-name "vigilia status"
      :component-did-mount update-logger-state!
      :reagent-render
      (fn []
        (cond 
              
              @error-a [:div.alert.alert-danger "Uh oh... problem communicating with Wacnet."
                        [:div [:button.btn.btn-default {:on-click update-logger-state!} "Try again"]]]

              @logger-state
              (let [logger @logger-state
                    ss (:scanning-state logger)
                    project-id (:project-id @configs-a)]
                [:div
                 [:div.text-center.well
                  [:h3 "Logging : " (if (:logging? logger)
                                      [:span.text-success "In progress"]
                                      [:span.text-danger "Stopped"])]
                  [:div [:button.btn.btn-success {:on-click #(send-command! true)
                                                  :disabled (or (:logging? logger) (not project-id))} "Start"] " "
                   [:button.btn.btn-danger {:on-click #(send-command! false)
                                            :disabled (or (not (:logging? logger)) (not project-id))} "Stop"]]
                  (if project-id
                    [link-to-project (:api-root @configs-a) project-id]
                    [:div.alert.alert-danger {:style {:margin-top 10}} 
                     "Missing Project ID."
                     [:div "Fill the " [:b "configurations"] " section below."]])]
                 [:div.panel.panel-default
                  [:div.panel-heading.panel-toggle {:data-toggle "collapse" :data-target "#logging-status"
                                                    :style {:cursor :pointer}}
                   [:h4.panel-title.text-center "Details " [:i.fa.fa-chevron-down]]]
                  [:div.panel-collapse.collapse {:id "logging-status"}
                   [:div.panel-body
                    [:table.table.table-striped.table-hover
                  [:caption "Logging info"]
                  [:tbody
                   [:tr
                    [:td.col-sm-2 [:b "Local logs : "]]
                    [:td.col-sm-2 (str (:local-logs ss))]
                    [:td.col-sm-8 [:span "Local logs are logs that weren't sent back (yet) to the remote servers. "
                                   "Perhaps a connection problem? If they start to accumulate, "
                                   "you should investigate your network access."]]]

                   [:tr
                    [:td.col-sm-2
                     [:b "Last scan duration: "]]
                    [:td.col-sm-2 (gstring/format "%.2f" (double (/ (or (:scanning-time-ms ss)  0) 1000 60))) " min"]
                    [:td.col-sm-8
                     [:p "Don't set a scanning interval smaller than this value. "
                      "The time taken to do a scan is determined by your network. MS/TP devices WILL "
                      "take longer than IP or Ethernet devices."]]]
                   
                   [:tr
                    [:td.col-sm-2 [:b "Completed scans : "]]
                    [:td.col-sm-2 (str (or (:completed-scans ss) 0))]
                    [:td.col-sm-8 "The number of network scans completed since Wacnet was booted."]]

                   [:tr
                    [:td.col-sm-2 [:b "Currently scanning : "]]
                    [:td.col-sm-2 (if (:scanning? logger) "Yes" "No")]
                    [:td.col-sm-8 ""]]
                   
                   [:tr
                    [:td.col-sm-2 [:b "Scan progress : "]]
                    [:td.col-sm-2 (if (:scanning? logger) 
                                    (->> (/ (count (:ids-scanned logger)) 
                                            (count (:ids-to-scan logger)))
                                         (* 100)
                                         (gstring/format "%.1f"))) "0%"]
                    [:td.col-sm-8 ""]]]]]]]
                 
                 [:div.text-right [:button.btn.btn-default {:on-click update-logger-state!}
                                   "Refresh " (when @loading-a [:i.fa.fa-spinner.fa-pulse])]]
                 
                 ])))})))


;; (defn vigilia-controls []
;;   (let [status @timed/logging-state
;;         btn-disabled? (not (:project-id (scan/get-logger-configs)))]
;;     [:div.row
;;      [:div.col-md-4
;;       [:h4 "Logging Controls"]
;;       [:div.btn-toolbar
;;        (he/link-to "/vigilia/configs/stop-vigilia"
;;                    [:div.btn.btn-danger {:style "margin-left: 1em;"
;;                                          :disabled (= "Stopped" status)}
;;                     "Stop"])
;;        (he/link-to "/vigilia/configs/start-vigilia"
;;                    [:div.btn.btn-success {:style "margin-left: 1em;"
;;                                           :disabled 
;;                                           (if (= "Stopped" status)
;;                                             btn-disabled?
;;                                             true)}
;;                     "Start"])]]
;;      [:div.col-sm-6
;;       [:p "Wacnet will start the Vigilia logging automatically on startup if a "
;;        "Vigilia configuration file is found."]]]))

(defn vigilia-page []
  (let [configs-a (r/atom nil)]
    (fn []
      [common/scrollable
       [:div.container                
        ;(when @configs-a [explorer-modal configs-a])
        [heading]
        [:hr]
        [vigilia-status configs-a]
        [:hr]
        [configs-steps configs-a]]])))

