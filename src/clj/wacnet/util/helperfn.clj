(ns wacnet.util.helperfn
  (:require [clojure.edn :as edn]))

(defn safe-read
  "Evaluate the string in a safe way. If error while reading, return
  nil" [s]
  (try (binding [*read-eval* false]
         (read-string s)) ;;; EDN/read-string can't read regex!
       (catch Exception e)))

(defn parse-or-nil
  "Return the parsed string as an integer, or nil if there's garbage"
  [string]
  (let [result (try (Integer/parseInt string)
                    (catch Exception e))]
    (if (number? result)
      result nil)))

(defn parse-float-or-nil
  "Return the parsed string as a float, or nil if there's garbage"
  [string]
  (let [result (try (Float/parseFloat string)
                    (catch Exception e))]
    (if (number? result)
      result nil)))
