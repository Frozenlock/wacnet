(ns wacnet.handler
  (:require [reagent.core :as r]
            ;[ajax.core :refer [GET POST]]
            [bidi.bidi :as bidi]
            [bidi.router :as router]
            [wacnet.routes :as routes]
            [goog.events :as events]
            [goog.history.EventType :as EventType]
            [re-com.core :as re]
            [wacnet.explorer.devices :as d]
            [wacnet.local-device.configs :as cf]
            [wacnet.repl :as repl]
            [wacnet.vigilia :as vi]
            [clojure.string :as s])
  (:import goog.History))



(defn make-device-tab [params]
  (let [device-id (:device-id params)]
    [d/controllers-view device-id]))


(defn logo []
  (let [height "2.3em"]
    [:a {:href "/"
         :style {:line-height height}}
     [:img {:src "/img/wacnet-logo-name.svg"
            :style {:height height}}]]))



(defn default-tab-content []
  [:div "empty tab"])

(defonce current-tab-fn (r/atom default-tab-content))

(defn make-configs-tab [params]
  [cf/configs-form params])
;; In these tabs we put what is available to the user.

(def tabs {:devices {:name "Explorer"
                     :content #'make-device-tab}
           :local-device-configs {:name "Configs"
                                  :content #'make-configs-tab}
           :repl {:name "REPL"
                  :content #'repl/repl-page}
           :vigilia {:name "Vigilia"
                     :content #'vi/vigilia-page}
           })



(def current-tab-id (r/atom :devices))

(defn goto-tab! [tab-id]
  (reset! current-tab-id tab-id)
  (let [url (routes/path-for tab-id)]
    (routes/goto-hash! url)))

(defn navigate! [url]
  (prn "navigating to " url)
  (let [url-sans-hash (s/replace url "#" "")
        matched-route (bidi/match-route routes/app-routes url-sans-hash)]
    (do (print (str "matching "url-sans-hash " ----> " matched-route))
        (let [{:keys [handler route-params]} matched-route]
          (if-not handler
            (goto-tab! :devices) ;; default tab
            (do 
              (reset! current-tab-id handler)
              (reset! current-tab-fn
                      (partial (:content (get tabs handler))
                               route-params))))))))




(defn tab
  "Make the `tab` button. Use a different class if the tab is
  active." [k]
  (let [active? (= k @current-tab-id)]
    [:li {:class (when active? "active")
          :style {:cursor (when-not active? "pointer")}
          :on-click (when-not active? #(goto-tab! k))}
     [:a (:name (get tabs k))]]))


(defn horizontal-tabs [tabs-map]
  [:ul.nav.nav-tabs {:style {:margin "10px"
                             :display "inline-block"
                             :vertical-align "middle"}}
   (for [k (keys tabs)]
     ^{:key k}
     [tab k])])
       
;;        :error-handler prn}))

(defn main-tab []
  [@current-tab-fn])

(defn main-page []
  (let [title-atom (r/atom "")]
    (fn []
      [:div
       [:div {:style {:margin-left "10px" :margin-right "10px"}} 
        [:span  [logo]]
        [horizontal-tabs tabs]
        [:div {:style {:margin "10px"
                       :display "inline-block"
                       :vertical-align "middle"}}
         ]]           
       [main-tab]
       ])))



;; history
;; must be called after routes have been defined
(defn hook-browser-navigation! []
  (let [h (History.)]
    (events/listen h EventType/NAVIGATE
                   (fn [event]
                     (navigate! (str "#" (.-token event)))))
    (.setEnabled h true)))


