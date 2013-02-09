(ns wacnet.server
  (require [wacnet.handler :as h]
           [ring.adapter.jetty :refer [run-jetty]])
  (:gen-class))


(defn start-server []
  (run-jetty #'h/handler {:port 47800 :join? false}))


(defn -main [& m]
  (start-server))
