(ns wacnet.local-device.configs
  (:require [reagent.core :as r]
            [re-com.core :as re]
            [ajax.core :refer [GET POST]]
            [wacnet.templates.common :as common]))

(defn get-configs [configs-a loading-a error-a]
  (reset! loading-a true)
  (reset! error-a nil)
  (GET "/api/v1/bacnet/local-device/configs"
      {:handler (fn [response]
                  (reset! loading-a nil)
                  (reset! configs-a response))
       :response-format :transit
       :error-handler (fn [_]
                        (reset! loading-a nil)
                        (reset! error-a true))}))

(defn summary [config-a]
  (let [ld-summary (r/atom nil)
        get-summary! (fn []
                       (GET "/api/v1/bacnet/local-device"
                           {:response-format :transit
                            :handler #(reset! ld-summary %)
                            :error-handler (fn [%]
                                             (reset! ld-summary nil)
                                             (prn %))}))]
    (r/create-class
     {:component-did-mount get-summary!
      :reagent-render
      (fn []
        [:div.text-center [:h4 "Avaiblable Interfaces"]
         (for [interface (:available-interfaces @ld-summary)
               :let [i-name (:interface interface)
                     ips (:ips interface)
                     b-ad (:broadcast-address interface)]]
           ^{:key i-name}
           [:div.btn.btn-default {:on-click #(swap! config-a assoc :broadcast-address b-ad)
                  :style {:cursor :pointer}}
            [:b (:interface interface)]
            [:div 
             ;[:div "Ips : " (:ips interface) " "]
             [:div "Broadcast address : " (:broadcast-address interface)]]])])})))


(defn parse-configs
  "Parse some config fields into integers."
  [configs-map]
  (let [parse-maybe (fn [str-or-num]
                      (if (number? str-or-num) str-or-num
                          (js/parseInt str-or-num)))]
    (-> configs-map 
        (update-in [:device-id] parse-maybe)
        (update-in [:port] parse-maybe))))

(defn update-configs-btn [configs-a]
  (let [error? (r/atom nil)
        loading? (r/atom nil)
        success? (r/atom nil)
        update-configs! (fn [config-map]
                            (reset! loading? true)
                            (POST "/api/v1/bacnet/local-device/configs"
                                {:params (parse-configs config-map)
                                 :response-format :transit
                                 :keywords? true
                                 :handler (fn [resp]
                                            (reset! success? true)
                                            (reset! loading? nil)
                                            (reset! error? nil))
                                 :error-handler (fn [resp]
                                                  (reset! loading? nil)
                                                  (reset! success? nil)
                                                  (reset! error? true))}))]
    (fn []
      [:span
       (when @success?
         (js/setTimeout #(reset! success? nil) 3000)
         [:div.message [:div.alert.alert-success "Updated!"]])
       (when @error?
         (js/setTimeout #(reset! error? nil) 3000)
         [:div.message [:div.alert.alert-danger "Error : " (:status-text @error?)
                        [:div "The server encountered an error. Double check the configuration."]]])
       [:button.btn.btn-primary 
        {:on-click #(update-configs! (-> @configs-a 
                                         (select-keys [:description :broadcast-address :device-id :port
                                                       :apdu-timeout
                                                       :number-of-apdu-retries])))}
        "Update / Reboot device"]])))


(defn configs-form
  []
  (let [configs-a (r/atom nil)
        success? (r/atom nil)
        error? (r/atom nil)
        loading? (r/atom nil)]
    (r/create-class
     {:component-did-mount (fn [_]
                             (get-configs configs-a loading? error?))
      ;:should-component-update (fn [_ _ _] true)
      :reagent-render 
      (fn []
        [:div.container         
         [:h3.text-center "Local Device Configurations"]
         [:hr]
         [:div.form-horizontal          
          (if @loading? [re/throbber]
              (for [[k v] @configs-a]
                ^{:key k} 
                [common/form-group (name k) k
                 [common/live-edit :input configs-a k]]))
          [:div.text-right
           [update-configs-btn configs-a]]
          [summary configs-a]]])})))
