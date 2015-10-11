(ns wacnet.nrepl
  (:require [clojure.tools.nrepl.server :refer [start-server stop-server]]
            [clojure.tools.nrepl :as nrepl]
            [cider.nrepl :refer [cider-nrepl-handler]]))

(def server (atom nil))

(declare stop-nrepl)


(defn server-eval "Send an expression to eval on the nrepl server."
  [to-eval]
  (with-open [conn (nrepl/connect :port (:port @server))]
    (-> (nrepl/client conn 1000)    ; message receive timeout required
        (nrepl/message {:op "eval" :code (str to-eval)})
        nrepl/response-values)))

(def repl-init
  "Some stuff to do when initiating the repl. Mainly, we are loading
  the related bacure functions so the user doesn't have to change
  between namespace." 
  '(do (require '[clojure.repl :refer [doc source apropos]])
                           (require '[clojure.pprint :refer [pprint print-table]])
                           (require '[bacure.core :refer :all])
                           (require '[bacure.network :refer :all])
                           (require '[bacure.remote-device :refer :all])
                           (require '[bacure.local-device :refer :all])))


(defn start-nrepl
  "Start (or restart) a REPL on a given port. Default to 47999 if none provided."
  [& port]
  (stop-nrepl)
  (reset! server (start-server :port (or (first port) 47999) :handler cider-nrepl-handler))
  (server-eval repl-init))

(defn stop-nrepl
  "Stop any current nrepl server." []
  (when @server
    (stop-server @server)))
