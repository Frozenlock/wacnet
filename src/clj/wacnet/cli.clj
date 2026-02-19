(ns wacnet.cli
  "CLI option definitions — single source of truth for both
   the Java launcher (via generated help.txt) and Clojure arg parsing."
  (:require [clojure.tools.cli :as cli]))

(def cli-options
  [["-p" "--project-id ID" "Vigilia project ID"]
   ["-k" "--logger-key KEY" "Vigilia logger key"]
   [nil  "--logger-id ID" "Logger identifier (auto-generated if not set)"]
   [nil  "--api-root URL" "Vigilia API root URL"]
   ["-t" "--time-interval MINUTES" "Scan interval in minutes (minimum 5)"
    :parse-fn #(Integer/parseInt %)
    :validate [#(>= % 5) "Must be at least 5 minutes"]]
   [nil  "--web-port PORT" "Web server port"
    :default 47800
    :parse-fn #(Integer/parseInt %)]
   [nil  "--nrepl-port PORT" "nREPL port"
    :default 47999
    :parse-fn #(Integer/parseInt %)]
   [nil  "--no-browser" "Don't open browser on startup"]
   [nil  "--headless" "Run without GUI (no splash screen, no system tray, no browser)"]
   ["-h" "--help" "Show help"]])

(defn help-text []
  (let [{:keys [summary]} (cli/parse-opts [] cli-options)]
    (str "Wacnet - BACnet webserver and toolkit\n"
         "\n"
         "Usage: wacnet [options]\n"
         "\n"
         "Options:\n"
         summary "\n")))
