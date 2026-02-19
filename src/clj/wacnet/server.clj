(ns wacnet.server
  (:gen-class
   :main true)
  (:require [clojure.tools.cli :as tools-cli]
            [taoensso.timbre :as timbre]
            [vigilia-logger.configs :as configs]
            [wacnet.cli :as cli]
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
    ([] (reset-webserver 47800))
    ([port]
     (when-let [close-fn (-> @storage :close)]
       (println "Server closed")
       (close-fn))
     (reset! storage (yada/listener h/routes {:port port}))
     (println (str "Server started on port " port)))))

(defn close-splash-screen!
  "Close the Java launcher splash screen, if present." []
  (try
    (wacnet.Launcher/closeSplash)
    (catch Exception _)))

(defn- apply-vigilia-configs!
  "Merge CLI-provided Vigilia options into the existing configs.edn."
  [options]
  (let [vigilia-keys [:project-id :logger-key :logger-id :api-root :time-interval]
        vigilia-configs (->> (select-keys options vigilia-keys)
                             (filter (comp some? val))
                             (into {}))]
    (when (seq vigilia-configs)
      (configs/save! (merge (or (configs/fetch) {}) vigilia-configs))
      (println (str "Vigilia configs updated: " (keys vigilia-configs))))))

(defn -main [& args]
  (let [{:keys [options errors]} (tools-cli/parse-opts args cli/cli-options)]
    (when (:help options)
      (print (cli/help-text))
      (System/exit 0))
    (when errors
      (println "Error in command-line arguments:")
      (doseq [e errors] (println (str "  " e)))
      (System/exit 1))
    (apply-vigilia-configs! options)
    (let [headless-mode? (or (:headless options) (headless?))]
      (binding [timbre/*config* {:min-level :error}]
        (close-splash-screen!)
        (ld/initialize-with-exit-on-fail!)
        (nrepl/start-nrepl! (:nrepl-port options))
        (let [web-port (:web-port options)
              wacnet-url (str "http://localhost:" web-port)]
          (reset-webserver web-port)
          (println (str "\n\n"
                        "Web interface at " wacnet-url "\n"
                        "API (Swagger) at " wacnet-url "/api/v1\n"
                        "nREPL on port " (:nrepl-port options) "\n"))
          (when-not headless-mode?
            (when-not (:no-browser options)
              (try
                (clojure.java.browse/browse-url wacnet-url)
                (catch Exception _
                  (println (str "Error: Couldn't launch browser at " wacnet-url)))))
            (st/initialize-systray! web-port)))))))
