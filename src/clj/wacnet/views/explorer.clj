(ns wacnet.views.explorer
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout]]
            [bacure.remote-device :as rd]))
  
(defn explorer-layout []
  (layout
   [:divs
    [:div#explorer-app
     ;; message in case JS isn't active, or if the user has the lame ass IE.
     ;; Users: stop using IE damnit!
     [:div.container 
      [:span.bg-warning
       (str "You are running a non-JS version of the explorer. You should try to activate " 
            "javascript in your browser, or use a more recent browser.")]]]
    [:script {:type"text/javascript"} "wacnet.client.run();"]]))

(defroutes explorer-routes
  (GET "/explorer" []
    (do (future (rd/discover-network)) ;; resend WhoIs
        (explorer-layout))))
