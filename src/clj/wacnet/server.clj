(ns wacnet.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [wacnet.handler :as h]
            [wacnet.local-device :as ld]
            [wacnet.nrepl :as wnrepl]
            [wacnet.systray :as st]
            [clojure.java.browse])
  (:gen-class :main true))


(defn start-server []
  (run-jetty #'h/handler {:port 47800 :join? false}))

(defn close-splash-screen! []
  (when-let [ss (java.awt.SplashScreen/getSplashScreen)]
    (.close ss)))


(defn -main [& m]
  (close-splash-screen!) ;; first thing to do once clojure is loaded.
  (ld/initialize-with-exit-on-fail!) ;; we possibly exit at this point
  (wnrepl/start-nrepl)
  (start-server)
  (println (str "\n\n\n"
                "---> \n"
                "     See the web interface at http://localhost:47800.\n"
                "     You can also connect to the Clojure nrepl on port 47999."))
  (st/initialize-systray!)
  (clojure.java.browse/browse-url "http://localhost:47800"))
