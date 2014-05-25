(ns wacnet.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [wacnet.handler :as h]
            ;[wacnet.local-device :as ld]
            )
  (:gen-class :main true))


(defn start-server []
  (run-jetty #'h/handler {:port 47800 :join? false}))
  


(defn -main [& m]
  (ld/initialize)
  (start-server)
  (println (str "\n\n\n"
                "---> \n"
                "     See the web interface at http://localhost:47800.\n"
                "     You can also connect to the Clojure nrepl on port 47999.")))
