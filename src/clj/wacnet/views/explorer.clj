(ns wacnet.views.explorer
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout]]
            [bacure.remote-device :as rd]
            [vigilia-logger.scan :as scan]
            [bacure.read-properties-cached :as rpc]))
  
(defn explorer-layout []
  (let [pr-project-id (or (some-> (scan/get-logger-configs) :project-id pr-str) "null")
        vigilia-url (clojure.string/replace (scan/get-api-root) #"/api/.*" "")]
    (layout
     [:div
      (let [cache-ttl (rpc/get-cache-ttl)]
        (when (> cache-ttl 0)
          [:div.alert.alert-warning.alert-dismissable.fade.in
           {:style "margin-bottom: 0px"}
           (str "Properties cached for " (/ cache-ttl 1000 60)" minutes.")
           " Configure with "[:a {:href "/configs"} "cache-ttl"]"."
           [:a.close {:href "#" :data-dismiss "alert" :aria-label "close"} "×"]]))
      [:div#explorer-app
       ;; message in case JS isn't active, or if the user has the lame ass IE.
       ;; Users: stop using IE damnit!
       [:div.container 
        [:span.bg-warning
         (str "You are running a non-JS version of the explorer. You should try to activate " 
              "javascript in your browser, or use a more recent browser.")]]]
      [:script {:type"text/javascript"} 
       (str"wacnet.client.run(" pr-project-id","(pr-str vigilia-url) ");")]])))

(defroutes explorer-routes
  (GET "/explorer" []
    (do (future (rd/discover-network)) ;; resend WhoIs
        (explorer-layout))))
