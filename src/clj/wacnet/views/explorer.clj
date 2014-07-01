(ns wacnet.views.explorer
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout]]
            [bacure.remote-device :as rd]
            [wacnet.vigilia.logger.scan :as scan]))
  
(defn explorer-layout []
  (let [pr-project-id (some-> (scan/get-configs-only) :project-id pr-str)]
    (layout
     [:div
      [:div#explorer-app
       ;; message in case JS isn't active, or if the user has the lame ass IE.
       ;; Users: stop using IE damnit!
       [:div.container 
        [:span.bg-warning
         (str "You are running a non-JS version of the explorer. You should try to activate " 
              "javascript in your browser, or use a more recent browser.")]]]
      [:script {:type"text/javascript"} 
       (str"wacnet.client.run(" pr-project-id ");")]])))

(defroutes explorer-routes
  (GET "/explorer" []
    (do (future (rd/discover-network)) ;; resend WhoIs
        (explorer-layout))))
