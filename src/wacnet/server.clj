(ns wacnet.server
  (:require [wacnet.handler :as h]
            [ring.adapter.jetty :refer [run-jetty]]
            [wacnet.bacnet-utils :refer [first-boot]])
  (:gen-class :main true))


(defn start-server []
  (run-jetty #'h/handler {:port 47800 :join? false}))
  


(defn -main [& m]
  (first-boot) ;;start the bacnet device
  (start-server))
