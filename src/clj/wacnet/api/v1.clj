(ns wacnet.api.v1
  (:require [compojure.core :as comp]
            [liberator.core :as lib]
            [bacure.core :as b]
            [bacure.remote-device :as rd]
            [bacure.coerce :as c]
            [clojure.edn :as edn]))

(defn read-vector [o]
  (map edn/read-string o))

(lib/defresource devices-list [req]
  :available-media-types ["application/edn"]
  :handle-ok (fn [_] (rd/remote-devices-and-names))
  :handle-not-found "Resource not found")

(lib/defresource objects-list [device-id]
  :available-media-types ["application/edn"]
  :handle-ok (fn [_] (b/remote-objects (Integer/parseInt device-id)))
  :handle-not-found "Resource not found")

(lib/defresource object-props [device-id object-id props]
  :available-media-types ["application/edn"]
  :handle-ok (fn [_] 
               (first (binding [c/*drop-ambiguous* true]
                        (b/remote-object-properties (Integer/parseInt device-id) 
                                                    (read-vector object-id) 
                                                    (read-vector props)))))
  :handle-not-found  (str "Resource not found"))

(comp/defroutes api-routes
  (comp/ANY "/api/v1/devices-list" req (devices-list req))

  (comp/ANY "/api/v1/objects-list" [device-id]
    (objects-list device-id))

  (comp/ANY "/api/v1/object-props" [device-id object-id props] 
    (object-props device-id object-id props))
  
  (comp/ANY "/api/v1/reload-devices" [] 
    (rd/discover-network)))
