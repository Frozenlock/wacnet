(ns wacnet.vigilia
  (:require [reagent.core :as r]
            [re-com.core :as re]
            [ajax.core :refer [GET POST DELETE]]
            [re-com.core :as re]
            [wacnet.templates.common :as common]
            [clojure.string :as s]
            [goog.string :as gstring]
            [clojure.tools.reader :as reader]
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
   (form-group value-atom label info attrs identity))
  ([value-atom label info attrs parse-fn]
   [:div.form-group
    [:label label]
    [:input.form-control (merge attrs 
                                {:value @value-atom
                                 :on-change #(let [v (-> % .-target .-value parse-fn)]
                                               (reset! value-atom v))})]
    [:p.text-info info]]))

(defn logger-id-form [logger-config-a]
  [form-group 
   (r/cursor logger-config-a [:logger-id]) 
   "Logger ID (ex: logger-first-floor)"
   (str "When recording a network using multiple loggers, the logger ID "
        "can help you with troubleshooting.")])

(defn logger-logs-path-form [logger-config-a]
  [form-group
   (r/cursor logger-config-a [:logs-path])
   "Logs directory"
   [:span "Where the logs are temporarily stored if the Vigilia server is unreachable. Default is "
    [:code "logger/"] "."]])

(defn interval-form [logger-config-a]
  (let [parse-fn (fn [value] (when-not (empty? value)
                               (js/parseInt value)))
        value-a (r/cursor logger-config-a [:time-interval])]
    [form-group value-a     
     "Log interval (minutes)"
     (str "This is the time interval at which the BACnet network is scanned. "
          "Smaller interval means more precision, but also more network utilization. "
          "10 minutes is a great interval to see most HVAC behaviors. "
          "(Minimum interval is 5 minutes.) Note that this is the minimum interval. "
          "It might take longer if you have a large or slow network.")
     {:type :number :min 5
      :on-blur (fn [evt]
                 (let [v (-> evt .-target .-value parse-fn)]
                   (some->> v
                            (max 5)
                            (reset! value-a))))}
     parse-fn]))


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
        read-seq (fn [string] (let [strings (-> string
                                                (s/replace "," "")
                                                (s/split #" ")
                                                (distinct)
                                                (seq))]
                                (->> strings
                                     (map (fn [value]
                                            (when-not (empty? value)
                                              (js/parseInt value))))
                                     (remove nil?))))]
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
  (def aaa logger-config-a)
  (let [criteria-a (r/cursor logger-config-a [:criteria-coll])
        print-seq (fn [value] (or (s/join ", " (map pr-str value)) ""))
        read-seq (fn [string]
                   (->> (str "[" string "]")
                        (reader/read-string)))]
    (fn []
      [:div.form-group
       [:label "Filter by device properties"]
       [coll-area (print-seq @criteria-a)
        #(->> % .-target .-value read-seq
              (reset! criteria-a))
        (str {:property "value"} ", "{:property "value" :property2 "value"})]
       [:p.text-info (str "Use this if you want to discard some devices based on"
                          " their device properties.")]
       [:p.text-info (str "If a device matches all the properties from a single criteria map {}, "
                          "it's discarded. Let's say we want to discard every devices with"
                          " the model name \"BACstat\", with the object name which partly matches"
                          " \"room\". This is what this filter would look like:")]
       [:pre.pre-scrollable (str {:model-name "BACstat", :object-name "room"})]
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


(defn save-configs! [temp-config-a logger-config-a success-a loading-a error-a]
  (POST configs-api-url
      {:handler (fn [response]
                  (reset! loading-a nil)
                  (reset! success-a true)
                  (let [cfgs (:configs response)]
                    (reset! logger-config-a cfgs)
                    (reset! temp-config-a cfgs)))
       :params (->> (for [[k v] @temp-config-a]
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
  [temp-config-a logger-config-a]
  (let [error? (r/atom nil)
        loading? (r/atom nil)
        success? (r/atom nil)]
    (fn [temp-config-a logger-config-a]
      [:span
       (when @success?
         (js/setTimeout #(reset! success? nil) 3000)
         [:div.message [:div.alert.alert-success "Configs updated!"]])
       (when @error?
         (js/setTimeout #(reset! error? nil) 3000)
         [:div.message [:div.alert.alert-danger "Error : " (:status-text @error?)]])
       [:button.btn.btn-primary {:on-click #(save-configs! temp-config-a logger-config-a success? loading? error?)}
        "Save"]])))

(defn api-url-form [temp-config-a logger-config-a]
  (let [test-server-a (r/atom nil)
        loading? (r/atom nil)
        error? (r/atom nil)
        api-url-a (r/cursor temp-config-a [:api-root])
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
                  [:div "We can't connect to the Vigilia server at "
                   [:strong [:a {:href api-url} api-url]]]
                  [:div "Does this machine have a network access?"]])])]
           
           [form-group api-url-a "vigilia API URL (leave empty for default)" nil]
           [:button.btn.btn-default {:on-click test-server-fn} "Test URL"]
           [save-btn temp-config-a logger-config-a]]))})))


(defn credentials-form [temp-config-a logger-config-a]
  (let [test-server-a (r/atom nil)
        loading? (r/atom nil)
        error? (r/atom nil)
        test-server-fn (fn [] 
                         (load! "/api/v1/vigilia/logger/tests/credentials" 
                                (let [p-id (:project-id @temp-config-a)
                                      logger-key (:logger-key @temp-config-a)] 
                                  (when (and p-id logger-key)
                                    {:project-id p-id :logger-key logger-key}))
                                test-server-a loading? error?))]
    (r/create-class
     {:component-did-mount test-server-fn
      :reagent-render
      (fn []
        (let [{:keys [project-id logger-key]} @temp-config-a]
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
             [form-group (r/cursor temp-config-a [:project-id]) "Project ID" nil]]
            [:div.col-sm-6 
             [form-group (r/cursor temp-config-a [:logger-key]) "Logger Key"
              "You can configure it in your Vigilia configuration page."]]]
           [:button.btn.btn-default {:on-click test-server-fn} "Test credentials"]
           [save-btn temp-config-a logger-config-a (when-not (:credentials-valid? @test-server-a) true)]]))})))



(defn get-configs! 
  ([temp-configs-a configs-a] (get-configs! temp-configs-a configs-a nil nil))
  ([loading-configs-a configs-a loading-a error-a]
   (when loading-a (reset! loading-a true))
   (when error-a (reset! error-a nil))
   (GET configs-api-url
       {:handler (fn [response]
                   (when loading-a (reset! loading-a nil))
                   (let [cfgs (:configs response)]
                     (reset! configs-a cfgs)
                     (reset! loading-configs-a cfgs)))
        :response-format :transit
        :error-handler (fn [_]
                         (when loading-a (reset! loading-a nil))
                         (when error-a (reset! error-a true)))})))

(defn clear-configs! [temp-configs-a configs-a success-a error-a]
  (DELETE configs-api-url
      {:handler (fn [resp] 
                  (reset! success-a true) 
                  (get-configs! temp-configs-a configs-a))
       :error-handler #(reset! error-a %)}))

(defn clear-configs-btn
  "A 'save' button that will send the current configurations to
  Wacnet's API. Shows a success or error message to the
  user."
  [temp-configs-a logger-config-a disabled?]
  (let [error? (r/atom nil)
        loading? (r/atom nil)
        success? (r/atom nil)]
    (fn [temp-configs-a logger-config-a disabled?]
      [:span
       (when @success?
         (js/setTimeout #(reset! success? nil) 3000)
         [:div.message [:div.alert.alert-success "Configs deleted!"]])
       (when @error?
         (js/setTimeout #(reset! error? nil) 3000)
         [:div.message [:div.alert.alert-danger "Error : " (:status-text @error?)]])
       [:button.btn.btn-danger {:on-click #(clear-configs! temp-configs-a logger-config-a success? error?)
                                :disabled disabled?
                                :style {:margin-bottom "20px"}}
        "Delete configurations " (when @loading? [:i.fa.fa-spinner.fa-pulse])]])))


(defn explorer-form [configs-a]
  (let [show-explorer? (r/atom nil)
        ]
    (fn [configs-a]
      [:div.form-group
       [:div [:label "Filter individual objects"]]
       [:div.text-info 
        "You can select individual objects to be recorded (assuming the devices go through the previous "
        "filters.)"]
       [:div.text-right
        [:div.btn-group
         [:button.btn.btn-danger {:on-click #(swap! configs-a dissoc :target-objects)
                                  :disabled (when-not (seq (:target-objects @configs-a)) true)} "Clear all"]
         [:button.btn.btn-default {:on-click #(reset! show-explorer? true)} "Open explorer"]]]       
       
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
                   [:div.alert.alert-info.text-center {:style {:margin 5}}
                    [:b
                     "If not a single object is selected in a device, they are all recorded."]]
                   [dev/controllers-view {;:selection-a (r/cursor configs-a [:target-objects])
                                          :vigilia-mode (r/cursor configs-a [:target-objects])}]]]])])))


(defn proxy-form [logger-config-a]
  (let [proxy-host (r/cursor logger-config-a [:proxy-host])
        proxy-port (r/cursor logger-config-a [:proxy-port])
        proxy-user (r/cursor logger-config-a [:proxy-user])
        proxy-password (r/cursor logger-config-a [:proxy-password])]
    [:div.form-group
     [:h4 "Vigilia Server Proxy"]
     [:div.text-right
      [:button.btn.btn-sm.btn-danger
       {:disabled (when-not (or @proxy-host @proxy-port @proxy-user @proxy-password) true)
        :on-click (fn [_] 
                    (reset! proxy-host nil)
                    (reset! proxy-port nil)
                    (reset! proxy-user nil)
                    (reset! proxy-password nil))}
       "Clear proxy"]]
     [:div.row
      [:div.col-md-6
       [:label (str "Proxy host" " (ex: http://127.0.0.1)")]
       [:input.form-control {:value @proxy-host
                             :on-change #(let [v (some-> % .-target .-value)]
                                           (reset! proxy-host v))}]]
      [:div.col-md-6
       [:label (str "Proxy port" " (ex: 4341)")]
       [:input.form-control {:value @proxy-port
                             :type :number :min 0
                             :on-change #(let [v (some-> % .-target .-value js/parseInt)]
                                           (reset! proxy-port v))}]]]
     [:div.row
      [:div.col-md-6
       [:label (str "Proxy username")]
       [:input.form-control {:value @proxy-user
                             :on-change #(let [v (some-> % .-target .-value)]
                                           (reset! proxy-user v))}]]
      [:div.col-md-6
       [:label (str "Proxy password")]
       [:input.form-control {:value @proxy-password
                             :on-change #(let [v (some-> % .-target .-value)]
                                           (reset! proxy-password v))}]]]     
     [:p.text-info (str "Some networks require to use a proxy to communicate to the Internet.")]]))


(defn advanced-configs-panel [temp-config-a configs-a]
  [:div.panel.panel-default
   [:div.panel-heading.panel-toggle {:data-toggle "collapse" :data-target "#advanded-configs"
                                     :style {:cursor :pointer}}
    [:h4.panel-title.text-center "Advanced configs (optional) " [:i.fa.fa-chevron-down]]]
   [:div.panel-collapse.collapse {:id "advanded-configs"}
    [:div.panel-body
     [re/h-box
      :size "1"
      :gap "10px"
      :children 
      [[re/v-box
        :size "1"
        :children [[logger-id-form temp-config-a]
                   [logger-logs-path-form temp-config-a]
                   [interval-form temp-config-a]
                   [range-form temp-config-a]
                   [id-filter-form temp-config-a]
                   [explorer-form temp-config-a]]]
       [re/v-box
        :size "1"
        :children [                   [proxy-form temp-config-a]
                   [object-delay-form temp-config-a]
                   [criteria-filter-form temp-config-a]
                   [re/gap :size "1"]
                   [re/box :child 
                    [:div.text-right [save-btn temp-config-a configs-a nil]]]]]]]]]])


(defn configs-steps [configs-a]
  (let [temp-configs-a (r/atom nil)]
    (r/create-class
     {:component-did-mount #(get-configs! temp-configs-a configs-a)
      :reagent-render
      (fn [configs-a]
        [:div
         [:h2 {:id "Config"} "Configurations"]
         [:p.text-info {:style {:margin 10
                                :margin-left 0}} 
          "Wacnet will start the logging automatically on startup if a "
          "Vigilia configuration file is found."]
         [api-url-form temp-configs-a configs-a]
         [credentials-form temp-configs-a configs-a]
         [advanced-configs-panel temp-configs-a configs-a]
         [:div.text-right
          [clear-configs-btn
           temp-configs-a
           configs-a]]])})))


(defn link-to-project [root-api-url project-id]
  (let [base-vigilia-url (last (re-find #"(.*)/api" (or root-api-url
                                                        "https://vigilia.hvac.io/api")))]
    [:div {:style {:margin-top 10}} "See the logged data at your " 
     [:a {:href (str base-vigilia-url "/v/" project-id) :target "_blank"}
      "project page "[:i.fa.fa-external-link]]]))


(defn timer-el 
  "Timer element that will count down given a timer atom (timer-a)
  with a number. Once the timer-a reaches zero, the timeout-fn is
  executed.
  
  This component will stop any countdown if it is unmounted."
  [timer-a timeout-fn]
  (let [forget-me? (atom nil)
        tick-fn (fn this-fn []
                     (when-not @forget-me?
                       (js/setTimeout this-fn 1000)
                       (let [time @timer-a]
                         (cond 
                           (not (number? time)) nil
                           (> time 1) (swap! timer-a dec)
                           (= time 1) (do (reset! timer-a nil) (timeout-fn))
                           :else nil))))]
    (r/create-class {:component-did-mount tick-fn
                     :component-will-unmount #(reset! forget-me? true)
                     :reagent-render (fn [timer-a timeout-fn]
                                       [:div])})))

(defn vigilia-controls [logger-state-a logger-configs-a]
  (let [logger @logger-state-a
        configs @logger-configs-a
        {:keys [project-id logger-key]} configs
        logger-url "/api/v1/vigilia/logger"
        send-command! (fn [bool]
                        (POST logger-url
                            {:response-format :transit
                             :params {:logging bool}
                             :handler #(reset! logger-state-a %)}))
        disabled? (not (every? not-empty [project-id logger-key]))]
    [re/v-box
     :align :center
     :style {:opacity (when disabled? 0.5)}
     :children [[:div.text-center.well
                 [:h3 "Logging " (if (:logging? logger)
                                   [:b.text-success "in progress"]
                                   [:b.text-danger "stopped"])]
                 [:div
                  [:button.btn.btn-success {:on-click #(send-command! true)
                                            :disabled (or (:logging? logger) disabled?)} "START"] " "
                  [:button.btn.btn-danger {:on-click #(send-command! false)
                                           :disabled (not (:logging? logger))} "STOP"]]]]]))

(defn refresh-btn [timer-a update-logger-state!]
  (let [update-fn (fn []
                    (update-logger-state!)
                    (reset! timer-a 15))]
    [re/h-box
     :align :center
     :children [[timer-el timer-a update-fn]
                [re/button :label [:i.fa.fa-refresh.fa-fw]
                 :tooltip "Refresh now"
                 :class "btn-default btn-sm"
                 :on-click update-fn]
                [re/gap :size "5px"]
                [:span.text-muted "Refresh in "@timer-a "s"]]]))


(defn info-local-logs [scanning-state-a]
  (let [ss @scanning-state-a]
    [:div [:b "Local logs : "(:local-logs ss)]
     [re/info-button
      :info [:span "Local logs are logs that weren't sent back (yet) to the remote servers. "
             "Perhaps a connection problem? If they start to accumulate, "
             "you should investigate your network access."]]]))

(defn info-last-scan [scanning-state-a]
  (def bbb scanning-state-a)
  (let [ss @scanning-state-a
        st (when-let [st (:scanning-time-ms ss)]
             (if (> st 0) st))
        end-time (when st
                   (let [et (:end-time ss)]
                     (when (> et 0)
                       (-> et
                           (reader/read-string)
                           (js/Date.)))))]
    [:div
     [:b "Last scan duration : " (or (when st
                                       (str (-> st
                                                (/ 1000 60)
                                                (double)
                                                ((fn [num]
                                                   (gstring/format "%.2f" num))))
                                            " min"))
                                     "N/A")]
     [:small [:span.text-muted (when end-time
                                 (str " (Completed at "
                                      (-> end-time
                                          (.toTimeString)
                                          (s/split " ")
                                          (first))
                                      ")"))]]
     [re/info-button
      :info [:p "Don't set a scanning interval smaller than this value. "
                            "The time taken to do a scan is determined by your network. MS/TP devices WILL "
             "take longer than IP or Ethernet devices."]]]))

(defn info-progress [logger-state-a]
  (let [ids-scanned @(r/cursor logger-state-a [:ids-scanned])
        ids-to-scan @(r/cursor logger-state-a [:ids-to-scan])
        start-time @(r/cursor logger-state-a [:start-time])
        end-time @(r/cursor logger-state-a [:end-time])]
    [re/h-box
     :children [[:b "Current scan progress : "]
                [re/gap :size "5px"]
                (if (> start-time end-time)
                  [re/progress-bar
                   :model (->> (/ (count ids-scanned)
                                  (max (count ids-to-scan) 1))
                               (* 100)
                               (gstring/format "%.1f"))
                   :width "200px"
                   :striped? true]
                  [:span "Waiting for next scan."])]]))

(defn info-completed-scans [scanning-state-a]
  (let [ss @scanning-state-a]
    [:div [:b "Completed scans : "(str (or (:completed-scans ss) 0))]
     [re/info-button
      :info [:span "The number of network scans completed since Wacnet was booted."]]]))

(defn vigilia-info [logger-state-a update-logger-state!]
  (let [timer-a (r/atom 15)
        loading-a (r/atom nil)
        error-a (r/atom nil)]
    (fn [logger-state-a]
      (let [logger @logger-state-a
            scanning-state-a (r/cursor logger-state-a [:scanning-state])]
        [re/v-box
         :size "380px"
         :children [[refresh-btn timer-a update-logger-state!]
                    [re/gap :size "10px"]
                    [re/v-box
                     :children [[info-local-logs scanning-state-a]
                                [info-completed-scans scanning-state-a]
                                [info-last-scan scanning-state-a]
                                [info-progress scanning-state-a]]]]]))))

(defn missing-id []
  [:div.alert.alert-danger {:style {:margin-top 10}} 
   "Missing Project ID."
   [:div "Fill the " [:b "configurations"] " section below."]])

(defn vigilia-status [configs-a]
  (let [logger-state (r/atom nil)
        loading-a (r/atom nil)
        error-a (r/atom nil)
        logger-url "/api/v1/vigilia/logger"
        logger-url "/api/v1/vigilia/logger"
        update-logger-state! (fn []
                               (reset! loading-a true)
                               (GET logger-url
                                   {:response-format :transit
                                    :handler (fn [resp] 
                                               (reset! loading-a nil)
                                               (reset! error-a nil)
                                               (reset! logger-state resp))
                                    :error-handler (fn [resp]
                                                     (reset! loading-a nil)
                                                     (reset! error-a true)
                                                     (prn resp))}))]
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
                project-id (:project-id @configs-a)
                logger-key (:logger-key @configs-a)
                api-root (:api-root @configs-a)]
            [re/h-box
             :gap "20px"
             :size "1"
             :align :center
             :children [[vigilia-controls logger-state configs-a]
                        (if (every? not-empty [project-id logger-key])
                          [re/h-box
                           :size "1"
                           :align :center
                           :children [[vigilia-info logger-state update-logger-state!]
                                      [re/gap :size "1"]
                                      [link-to-project api-root project-id]
                                      [re/gap :size "1"]]]
                          [missing-id])]])))})))



(defn vigilia-page []
  (let [configs-a (r/atom nil)]
    (fn []
      [common/scrollable
       [:div.container
        [heading]
        [:hr]
        [vigilia-status configs-a]
        [:hr]
        [configs-steps configs-a]]])))

