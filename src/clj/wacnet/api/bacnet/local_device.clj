(ns wacnet.api.bacnet.local-device
  (:require [bacure.core :as b]
            [bacure.local-save :as save]
            [bacure.remote-device :as rd]
            [bacure.local-device :as ld]
            [bacure.network :as net]
            [yada.resource :refer [resource]]
            [bidi.bidi :refer [path-for]]
            [clojure.walk :as w]
            [schema.core :as s]
            [ring.swagger.schema :as rs]
            [yada.yada :as yada]
            [clojure.set :as cs]))


(def produced-types
  #{"application/transit+json"
    ;"application/transit+msgpack"
    "application/json" 
    "application/edn" 
    "text/html"})


(def consumed-types
  #{"application/transit+json"
    ;"application/transit+msgpack"
    "application/json" 
    "application/edn"})

(def allowed-configs-keys
  #{:broadcast-address :device-id :port
    :apdu-timeout
    :number-of-apdu-retries :description
    :object-name
    :foreign-device-target})

;; (defn remove-nils [m]
;;   (->> (for [[k v] m]
;;          (when v [k v]))
;;        (remove nil?)
;;        (into {})))

(defn get-current-configs
  "Get the configuration from the local-device or (if not found) the
  configuration file."
  [local-device-id]
  (select-keys (merge ld/default-configs
                      (or (ld/local-device-backup)
                          (save/get-configs)))
               allowed-configs-keys))

(defn reset-and-save!
  "Reset the local device and save the configurations."
  [configs]
  (let [current-configs (ld/local-device-backup)]
    (ld/clear-all!) ;; remove all local devices
    ;; boot the local device
    (b/boot-up! (merge current-configs configs))
    ;; create a config backup and save it locally
    (select-keys (ld/save-local-device-backup!)
                 allowed-configs-keys)))


(s/defschema ForeignDevice
  {(s/optional-key :host) s/Str (s/optional-key :port) s/Int})

(s/defschema LocalConfigs
  {(s/optional-key :broadcast-address) s/Str
   (s/optional-key :device-id) s/Int
   (s/optional-key :port) s/Int
   (s/optional-key :description) s/Str
   (s/optional-key :apdu-timeout) s/Int
   (s/optional-key :apdu-segment-timeout) s/Int
   (s/optional-key :number-of-apdu-retries) s/Int
   (s/optional-key :object-name) s/Str
   (s/optional-key :foreign-device-target) (s/maybe ForeignDevice)})


(def bacnet-configs
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
                :charset "UTF-8"}]
    :methods {:get {:summary "BACnet configs"
                    :description "BACnet configs for the Wacnet local device."
                    :swagger/tags ["Local Device"]
                    :response (fn [ctx]
                                (get-current-configs nil))}
              :post {:description (str "Update the provided fields in the local device configs. "
                                       "Will automatically reboot the local device with the new configs.")
                     :swagger/tags ["Local Device"]
                     :parameters {:body LocalConfigs}
                     :response (fn [ctx]
                                 (let [new-configs (some-> ctx :parameters :body)]
                                   (when new-configs
                                     (reset-and-save! new-configs))))}}}))

(defn local-device-summary [local-device-id]
  (let [initialized? (some-> (ld/local-device-object local-device-id) (.isInitialized))]
    (merge {:initialized initialized?
            :available-interfaces (net/interfaces-and-ips)}
           (when initialized? {:known-remote-device (count (rd/remote-devices local-device-id))}))))

(def local-device
  (resource
   {:produces [{:media-type produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type consumed-types
                :charset "UTF-8"}]
    :methods {:get {:summary "Local device"
                    :description "BACnet local device info"
                    :swagger/tags ["Local Device"]
                    :response (fn [ctx]
                                (local-device-summary nil))}}}))
