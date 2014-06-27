(ns wacnet.api.v1
  (:require [compojure.core :as comp]
            [liberator.core :as lib]
            [bacure.core :as b]
            [bacure.remote-device :as rd]
            [bacure.coerce :as c]
            [clojure.edn :as edn]
            [clojure.set]
            [clojure.data.csv :as csv]))

(defn read-vector [o]
  (map edn/read-string o))

(defn oid->dotted-id [object-identifier]
  (let [[object-type object-instance] object-identifier]
    (str (.intValue (c/c-object-type object-type)) "." object-instance)))

(defn decode-dotted-identifier 
  "Take a dotted identifier such as 8.2 and transform it into a vector
  ['8' '2']." [oid]
  (clojure.string/split oid #"\."))

(defn make-dotted-identifier
  "Add a dotted identifier {:object-id \"12.1\"} to the object map."
  [object-map]
  (assoc object-map :object-id (oid->dotted-id (:object-identifier object-map))))

(defn add-object-instance-and-type [object-map]
  (let [[type instance] (decode-dotted-identifier (:object-id object-map))]
    (assoc object-map :object-type type :object-instance instance)))

(defn get-device-summary
  "Make a map of useful device info: 
   {:objects ... :update ... :scan-duration ... :name ...}" 
  [project-id device-id]
  (-> (map merge (b/remote-object-properties device-id [:device device-id] [:object-name :object-list]))
      (first)
      (clojure.set/rename-keys {:object-name :name :object-list :objects})
      (dissoc :object-identifier)
      (update-in [:objects] #(map oid->dotted-id %))))

(defn get-objects [device-id]
  (binding [c/*drop-ambiguous* true]
    (for [object (b/remote-object-properties device-id (b/remote-objects device-id)
                                             [:object-name :description :present-value])
          :when (and (:object-name object)(:description object) 
                     (not-empty object))]
      (-> object
          (clojure.set/rename-keys {:object-name :name :present-value :value})
          ;(dissoc :value)
          (make-dotted-identifier)
          (add-object-instance-and-type)
          ))))
  
(defn get-object-all-properties
  [device-id dotted-id]
  (binding [c/*drop-ambiguous* true]
    (-> (b/remote-object-properties device-id [(read-vector (decode-dotted-identifier dotted-id))] :all)
        (first)
        (clojure.set/rename-keys {:object-name :name :present-value :value})
                                        ;(dissoc :value)
        (make-dotted-identifier)
        (add-object-instance-and-type)
        )))

;;; the 'project-id' arguments are for compatibility with the HVAC.IO
;;; website.

;;; COMMON API

(lib/defresource devices-list [project-id]
  :available-media-types ["application/edn"]
  :handle-ok (fn [_] (for [[id name] (rd/remote-devices-and-names)]
                       [(str id) name]))
  :handle-not-found "Resource not found")
;; [["10100" "System A1"] ["10200" "System A2"]]

(lib/defresource objects [project-id device-id]
  :available-media-types ["application/edn"]
  :handle-ok (fn [_] (get-objects (edn/read-string device-id)))
  :handle-not-found "Resource not found")
;; [{:value 24.5 :description "" :object-instance "1" :object-type "12" :object-id "12.1" ...}
;;  {:value 33.1 :description "" :object-instance "2" :object-type "12" :object-id "12.2"...}]


(lib/defresource device-summary [project-id device-id]
  :available-media-types ["application/edn"]
  :handle-ok (fn [_] (get-device-summary project-id (edn/read-string device-id)))
               
  :handle-not-found "Resource not found")
;; {:objects ["12.1" "12.2"...] :scan-duration 5000 :update 1399919564512 :name "System A1"}




;;;================================================================
;;; WACNET only API

(lib/defresource trend-log [device-id trend-log-instance]
  :available-media-types ["application/csv"]
  :handle-ok (fn [ctx] (with-out-str
                         (->> (b/read-trend-log (Integer/parseInt device-id) 
                                                [:trend-log (Integer/parseInt trend-log-instance)])
                              (:log-buffer)
                              (csv/write-csv *out*))))
  :handle-not-found "Resource not found")


(lib/defresource object-all-properties [project-id device-id dotted-id]
  :available-media-types ["application/edn"]
  :handle-ok (fn [_] (get-object-all-properties (edn/read-string device-id) dotted-id))
  :handle-not-found "Resource not found")

;; (lib/defresource object-props [device-id object-id props]
;;   :available-media-types ["application/edn"]
;;   :handle-ok (fn [_] 
;;                (first (binding [c/*drop-ambiguous* true]
;;                         (b/remote-object-properties (Integer/parseInt device-id) 
;;                                                     (read-vector object-id) 
;;                                                     (read-vector props)))))
;;   :handle-not-found  (str "Resource not found"))


(comp/defroutes api-routes
  
  (comp/GET "/api/v1/devices-list/:project-id" [project-id] 
    (devices-list project-id))

  (comp/GET "/api/v1/objects/:project-id/:device-id" [project-id device-id] 
    (objects project-id device-id))

  (comp/GET "/api/v1/object-all-properties/:project-id/:device-id/:dotted-id" [project-id device-id dotted-id]
    (object-all-properties project-id device-id dotted-id))

  (comp/GET "/api/v1/device-summary/:project-id/:device-id" [project-id device-id] 
    (device-summary project-id device-id))

  ;;; Wacnet only API
  (comp/GET "/api/v1/trend-log/:project-id/:device-id/:trend-log-instance/:filename"
      [project-id device-id trend-log-instance]
    (do (println (str project-id " ---" device-id "-----" trend-log-instance))
         (trend-log device-id trend-log-instance)))
)
