(ns wacnet.api
  (:require [aleph.http :refer [start-server]]
            [bidi.ring :refer [make-handler] :as bidi]
            [yada.yada :refer [yada] :as yada]
            [yada.body :as yb]
            [cognitect.transit :as transit]
            [wacnet.api.bacnet.devices :as bd]
            [wacnet.api.bacnet.local-device :as bld]
            [wacnet.api.vigilia-logger :as log]
            [wacnet.api.repl :as repl]
            [bacure.core :as b])
  ;; (:import [org.joda.time DateTime ReadableInstant])
  )


;;; add JodaTime conversion

;; Most of the stuff is taken from yada.body.
;; Should keep an eye for an easier way to do this.

;; (defn ^:private transit-encode [v type]
;;   (let [baos (java.io.ByteArrayOutputStream. 100)]
;;     (transit/write (transit/writer baos type) v)
;;     (.toByteArray baos)))

;; (def joda-time-writer
;;   (transit/write-handler
;;     (constantly "m")
;;     (fn [v] (-> ^ReadableInstant v .getMillis))
;;     (fn [v] (-> ^ReadableInstant v .getMillis .toString))))


;; ;; json conversion
;; (defmethod yada.body/render-map "application/transit+json"
;;   [data _]
;;   (transit-encode data :json {:handlers {org.joda.time.DateTime joda-time-writer}}))

;; (defmethod yada.body/render-seq "application/transit+json"
;;   [data _]
;;   (transit-encode data :json {:handlers {org.joda.time.DateTime joda-time-writer}}))


;; ;; msgpack conversion
;; (defmethod yada.body/render-map "application/transit+msgpack"
;;   [data _]
;;   (transit-encode data :msgpack {:handlers {org.joda.time.DateTime joda-time-writer}}))

;; (defmethod yada.body/render-seq "application/transit+msgpack"
;;   [data _]
;;   (transit-encode data :msgpack {:handlers {org.joda.time.DateTime joda-time-writer}}))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;



(defn make-api-routes 
  [api-path]
  [api-path
   (yada/swaggered
    ["/" [["repl" repl/eval-resource]

          ["devices" [["" bd/devices-list]
                      [["/" :device-id] [["" bd/device]
                                         ["/objects" [["" bd/objects]
                                                      [["/" :object-id] bd/object]]]]]]]
          ["local-device" [["" bld/local-device]
                           ["/" [["configs" bld/bacnet-configs]]]]]
          log/api-route]]
    {:info {:title "Wacnet API"
            :version "1.0"
            :description "API for Wacnet multiple features"
            :contact {:name "HVAC.IO"
                      :email "contact@hvac.io"
                      :url "https://hvac.io"}}
     :tags [{:name "Vigilia"
             :description ""}
            {:name "REPL"
             :description "Live command execution"}
            {:name "BACnet"
             :description "Access to the BACnet network"}
            {:name "Local Device"
             :description "BACnet local device"}]
     :basePath api-path})])


(comment (def server (start-server
                      (bidi/make-handler (make-api-routes "/api/v1"))
                      {:port 3000})))


(comment
  (yada/response-for (make-api-routes "") :get "/devices"))
