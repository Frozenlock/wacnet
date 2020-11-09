(ns wacnet.server
  (:gen-class
   :main true)
  (:require [taoensso.timbre :as timbre]
            [wacnet.handler :as h]
            [wacnet.local-device :as ld]
            [wacnet.nrepl :as nrepl]
            [wacnet.systray :as st]
            [yada.yada :as yada]))

(defn headless?
  "True if we are running without any graphical support."
  []
  (java.awt.GraphicsEnvironment/isHeadless))


(defn start-webserver []
  (yada/listener h/routes {:port 47800}))

(defn close-splash-screen!
  "Close the splash screen, if present."[]
  (when-not (headless?)
    (when-let [ss (java.awt.SplashScreen/getSplashScreen)]
      (.close ss))))


(defn -main [& m]
  (binding [timbre/*config* {:min-level :error}]
    (close-splash-screen!) ;; first thing to do once clojure is loaded.
    (ld/initialize-with-exit-on-fail!) ;; we possibly exit at this point
    (nrepl/start-nrepl!)
    (start-webserver)
    (println (str "\n\n\n"
                  "---> \n"
                  "     See the web interface at http://localhost:47800.\n"
                  "     You can also connect to the Clojure nrepl on port 47999."))
    ;; when we have graphical support
    (when-not (headless?)
      ;; add the system tray icon
      (st/initialize-systray!)
      ;; and open the web interface
      (clojure.java.browse/browse-url "http://localhost:47800"))))
