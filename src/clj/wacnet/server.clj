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

(let [storage (atom nil)]
  (defn reset-webserver
    []
    (when-let [close-fn (-> @storage :close)]
      (println "Server closed")
      (close-fn))
    (reset! storage (yada/listener h/routes {:port 47800}))
    (println "Server started")))

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
    (reset-webserver)
    (let [wacnet-url "http://localhost:47800"]
      (println (str "\n\n"
                    "See the web interface at " wacnet-url ".\n"
                    "You can also connect to the Clojure nrepl on port 47999.\n"))
      ;; when we have graphical support
      (when-not (headless?)
        ;; and open the web interface
        (try
          (clojure.java.browse/browse-url wacnet-url)
          (catch Exception _
            (println (str "Error: Couldn't launch browser at " wacnet-url))))
        ;; add the system tray icon
        (st/initialize-systray!)))))
