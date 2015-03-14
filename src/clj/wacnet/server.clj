(ns wacnet.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [wacnet.handler :as h]
            [wacnet.local-device :as ld]
            [wacnet.nrepl :as wnrepl]
            [wacnet.systray :as st]
            [clojure.java.browse])
  (:gen-class :main true))


(defn headless?
  "True if we are running without any graphical support."
  []
  (java.awt.GraphicsEnvironment/isHeadless))


(defn start-server []
  (run-jetty #'h/handler {:port 47800 :join? false}))

(defn close-splash-screen!
  "Close the splash screen, if present."[]
  (when-not (headless?)
    (when-let [ss (java.awt.SplashScreen/getSplashScreen)]
      (.close ss))))


(defn -main [& m]
  (close-splash-screen!) ;; first thing to do once clojure is loaded.
  (ld/initialize-with-exit-on-fail!) ;; we possibly exit at this point
  (wnrepl/start-nrepl)
  (start-server)
  (println (str "\n\n\n"
                "---> \n"
                "     See the web interface at http://localhost:47800.\n"
                "     You can also connect to the Clojure nrepl on port 47999."))
  ;; when we have graphical support
  (when-not (headless?)
    ;; add the system tray icon
    (st/initialize-systray!)
    ;; and open the web interface
    (clojure.java.browse/browse-url "http://localhost:47800")))
