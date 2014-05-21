(ns wacnet.local-device
  (:require [bacure.core]
            [bacure.local-device :as ld]
            [logger.timed :as logger]
            [trptcolin.versioneer.core :as version]))

(defn initialize
  "Initialize the local BACnet device." []
  ;(when-not (logger/maybe-start-logging) ;; start logging
  (bacure.core/boot-up
   {:vendor-name "BACnethelp.com"
    :vendor-identifier 697
    :model-name "Wacnet"
    :object-name "Wacnet webserver"
    :application-software-version (version/get-version "wacnet" "wacnet")
    :description 
    (str "Wacnet: a BACnet webserver, but also a portable BACnet toolkit! \n"
         "Access the web interface at \n"
         "http://"(bacure.network/get-any-ip)":47800, "
         "or use the Clojure REPL on port " (:port @wacnet.nrepl/server)".")})
  (logger/maybe-start-logging))
