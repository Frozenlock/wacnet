(ns wacnet.gen-help
  "Build step: generates resources/help.txt from CLI option definitions.
   Run via: lein run -m wacnet.gen-help"
  (:require [clojure.java.io :as io]
            [wacnet.cli :as cli]))

(defn -main [& _]
  (let [path "resources/help.txt"]
    (io/make-parents path)
    (spit path (cli/help-text))
    (println (str "Generated " path))))
