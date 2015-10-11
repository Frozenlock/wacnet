(ns wacnet.local-device
  (:require [bacure.core]
            [bacure.local-device :as ld]
            [bacure.read-properties-cached :as rpc]
            [vigilia-logger.timed :as timed]
            [trptcolin.versioneer.core :as version]
            [wacnet.nrepl]))

(defn initialize
  "Initialize the local BACnet device." []
  ;(when-not (logger/maybe-start-logging) ;; start logging
  (bacure.core/boot-up
   {:vendor-name "HVAC.IO"
    :vendor-identifier 697
    :model-name "Wacnet"
    :object-name "Wacnet webserver"
    :application-software-version (version/get-version "wacnet" "wacnet")
    :description 
    (str "Wacnet: BACnet webserver and toolkit. \n"
         "Access the web interface at \n"
         "http://"(bacure.network/get-any-ip)":47800, "
         "or use the Clojure nREPL on port " (:port @wacnet.nrepl/server)".")})
  (timed/maybe-start-logging))


(defn headless?
  "True if we are running without any graphical support."
  []
  (java.awt.GraphicsEnvironment/isHeadless))


(defn initialize-with-exit-on-fail!
  "Try to initialize the local device. If we can't bind to the BACnet
  port, show a message to the user and then exit."
  []
  (try (initialize)
       (rpc/set-cache-ttl! (rpc/get-cache-ttl))
       (catch java.net.BindException e
         (let [err-msg (str "\n*Error*: The BACnet port ("(or (:port (ld/get-configs)) 47808)")"
                            " is already bound to another software.\n\t "
                            "Please close the other software and try again.\n")]
           (do (if (headless?)
                 (println err-msg) ;; print to console if headless
                 (javax.swing.JOptionPane/showMessageDialog
                  nil
                  err-msg
                  "Error"
                  javax.swing.JOptionPane/ERROR_MESSAGE))
               (System/exit 0))))))
