(ns wacnet.local-device
  (:require [bacure.core]
            [bacure.local-device :as ld]
            [bacure.local-save :as ls]
            ;[bacure.read-properties-cached :as rpc]
            [vigilia-logger.timed :as timed]
            [trptcolin.versioneer.core :as version]            
            [clojure.stacktrace :as st]))

(defn initialize!
  "Initialize the local BACnet device." []
  (let [saved-configs (ls/get-configs)
        {:keys [object-name description]} saved-configs]
    (bacure.core/boot-up!
     {:vendor-name "HVAC.IO"
      :vendor-identifier 697
      :model-name "Wacnet"
      :object-name (or object-name "Wacnet webserver")
      :application-software-version (version/get-version "wacnet" "wacnet")
      :description (or description
                       (str "Wacnet: BACnet webserver and toolkit. "
                            "Access the web interface at "
                            "http://"(bacure.network/get-any-ip)":47800, "
                            "or use the Clojure nREPL on port 47999" 
                            "."))}))
  (timed/maybe-start-logging))

(defn java-version
  "Return the current java version as double."[]
  (-> (System/getProperty "java.specification.version")
      (Double/parseDouble)))

(defn headless?
  "True if we are running without any graphical support."
  []
  (java.awt.GraphicsEnvironment/isHeadless))

(defn enforce-min-java!
  "Enforce the minimum required java version."[]
  (let [min-version 1.8
        current-version (java-version)]
    (when-not (>= current-version min-version)
      (let [err-msg (str "You need Java "min-version " or higher to run this application. \n"
                         "(You are using Java "current-version ".)")]
        (do (if (headless?)
              (println err-msg) ;; print to console if headless
              (javax.swing.JOptionPane/showMessageDialog
               nil
               err-msg
               "Error"
               javax.swing.JOptionPane/ERROR_MESSAGE))
            (System/exit 0))))))


(defn initialize-with-exit-on-fail!
  "Try to initialize the local device. If we can't bind to the BACnet
  port or encounter an error, show a message to the user and then exit."
  []
  (try (initialize!)
       (enforce-min-java!)
       ;; port already taken exception
       (catch java.net.BindException e
         (let [err-msg (str "\n*Error*: The BACnet port ("(or (:port (:init-configs (ld/get-local-device nil))) 
                                                              47808)")"
                            " is already bound to another software.\n\t "
                            "Please close the other software and try again.\n")]
           (do (if (headless?)
                 (println err-msg) ;; print to console if headless
                 (javax.swing.JOptionPane/showMessageDialog
                  nil
                  err-msg
                  "Error"
                  javax.swing.JOptionPane/ERROR_MESSAGE))
               (System/exit 0))))
       
       ;; configs and other errors
       (catch java.lang.Exception e
         (let [err-msg (str "\n*Error*: "
                            "The application encountered an error while initiating. Try deleting the "
                            "configs.clj file. If the problem persists, you can contact us and include "
                            "the following stacktrace : \n\n"
                            (with-out-str (st/print-stack-trace e)))]
           (do (if (headless?)
                 (println err-msg) ;; print to console if headless
                 (javax.swing.JOptionPane/showMessageDialog
                  nil
                  (javax.swing.JTextArea. err-msg)
                  "Error"
                  javax.swing.JOptionPane/ERROR_MESSAGE))
               (System/exit 0))))))
