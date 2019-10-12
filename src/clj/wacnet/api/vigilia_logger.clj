(ns wacnet.api.vigilia-logger
  (:require [bacure.core :as b]
            [yada.resource :refer [resource]]
            [bidi.bidi :refer [path-for]]
            [schema.core :as s]
            [ring.swagger.schema :as rs]
            [yada.yada :as yada]
            [clojure.set :as cs]
            [vigilia-logger.scan :as scan]
            [vigilia-logger.timed :as timed]
            [wacnet.api.util :as u]
            [wacnet.bacnet-utils :as bu]
            [clojure.java.io :as io]
            [bacure.local-save :as local]
            [clojure.string :as str])
  (:import java.io.File))




(declare api-route
         configs)


(defn scanning-state-resp [ctx]
  {:href (u/make-link ctx)
   :logging? (timed/is-logging?)
   :scanning-state (-> @scan/scanning-state
                       (update-in [:start-time] #(-> % .getMillis .toString))
                       (update-in [:end-time] #(-> % .getMillis .toString))
                       (assoc :logging (timed/is-logging?)
                              :local-logs (count (scan/find-unsent-logs))))
   :configs {:href (u/make-link ctx (str "/" (path-for api-route configs)))}})

(def scanning-state
  (resource 
   {:produces [{:media-type u/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type u/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:post {:description "Start or stop the Vigilia logging."
                     :swagger/tags ["Vigilia"]
                     :parameters {:body  {:logging (rs/field s/Bool {:description "True will start the logging, false will stop it."})}}
                     :response (fn [ctx]
                                 (let [logging (some-> ctx :parameters :body :logging)]
                                   (if logging 
                                     (timed/restart-logging)
                                     (timed/stop-logging))
                                   (scanning-state-resp ctx)))}
              :get {:description (str "Various information about the state of the current scan (if any). Fields include: \n"
                                  "- scanning? \n"
                                  "- start-time \n"
                                  "- end-time \n"
                                  "- scanning-time-ms \n"
                                  "- ids-to-scan \n"
                                  "- ids-scanning \n"
                                  "- ids-scanned \n")
                    :swagger/tags ["Vigilia"]
                    :response scanning-state-resp}}}))

(s/defschema Criterias
  {s/Keyword s/Any})

(s/defschema TargetObjects
  {s/Str [s/Str]})



(s/defschema Configs
  {(s/optional-key :api-root) s/Str
   (s/optional-key :logger-id) s/Str
   (s/optional-key :logger-version) s/Str
   (s/optional-key :logger-key) s/Str
   (s/optional-key :project-id) s/Str
   (s/optional-key :min-range) s/Int
   (s/optional-key :max-range) s/Int
   (s/optional-key :id-to-remove) [s/Int]
   (s/optional-key :id-to-keep) [s/Int]
   (s/optional-key :time-interval) s/Int
   (s/optional-key :criteria-coll) [Criterias]
   (s/optional-key :object-delay) s/Int
   (s/optional-key :target-objects) TargetObjects
   (s/optional-key :proxy-host) s/Str
   (s/optional-key :proxy-port) s/Int
   (s/optional-key :proxy-user) s/Str
   (s/optional-key :proxy-password) s/Str
   (s/optional-key :logs-path) s/Str})

(defn expected-keys []
  (for [[m _] Configs]
    (-> m first second)))

(defn decode-target-objects [config-map]
  (if (:target-objects config-map)
    (update-in config-map [:target-objects]
               (fn [target-map]
                 (->> (for [[device-id obj-id-coll] target-map]
                        [(Integer/parseInt device-id)
                         (vec (map bu/short-id-to-identifier obj-id-coll))])
                      (into {}))))
    config-map))

(defn encode-target-objects [config-map]
  (if (:target-objects config-map) 
    (update-in config-map [:target-objects]
               (fn [target-map]
                 (->> (for [[device-id obj-id-coll] target-map]
                        [(str device-id)
                         (vec (map bu/identifier-to-short-id obj-id-coll))])
                      (into {}))))
    config-map))

(defn validate-logs-path!
  "Check if the provided path exists. If it doesn't, try to create it.
  Return the path if it's valid."
  [path]
  (when (seq path)
    (let [normalized-path (str/replace path (re-pattern (str/re-quote-replacement java.io.File/separator)) "/")
          path (str (str/replace normalized-path (re-pattern "[/]$") "") "/") ;; insure the path ends with '/'
          dir (io/as-file path)]
      (when (or
             ;; if it exists, make sure it's a directory.
             (let [dir (io/as-file path)]
               (when (.exists dir)
                 (.isDirectory dir)))

             ;; if it doesn't exist, try to create it and then check if it's a
             ;; directory.
             (do (io/make-parents (str path "dummy-filename"))
                 (.isDirectory (io/as-file path))))
        path))))

(def configs
  (let [response-map-fn (fn [ctx]                
                          (let [configs (-> (scan/get-logger-configs)
                                            (encode-target-objects))]
                            {:href    (u/make-link ctx)
                             :vigilia {:href (when-let [p-id (:project-id configs)] 
                                               (str (->> (scan/get-api-root)
                                                         (re-find #"(.*)/api/")
                                                         (last))
                                                    "/v/" p-id))}
                             :configs (merge (into {} (for [k (expected-keys)]
                                                        [k nil]))
                                             configs)}))]
    (resource 
     {:produces       [{:media-type u/produced-types
                        :charset    "UTF-8"}]
      :consumes       [{:media-type u/consumed-types
                        :charset    "UTF-8"}]
      :access-control {:allow-origin "*"}
      :methods        {:get    {:description  "Logger configurations"
                                :swagger/tags ["Vigilia"]
                                :response     response-map-fn}
                       :delete {:description  (str "Delete the current logger configurations. "
                                                   "This will prevent Wacnet from automatically scanning devices on boot.")
                                :swagger/tags ["Vigilia"]
                                :response     (fn [ctx]
                                                (scan/delete-logger-configs!))}
                       :post   {:description  "Update the provided fields in the Vigilia logger configs."
                                :swagger/tags ["Vigilia"]
                                :parameters   {:body Configs}
                                :response     (fn [ctx]
                                                (when-let [new-configs (some-> ctx :parameters :body
                                                                               decode-target-objects
                                                                               (update :logs-path validate-logs-path!))]
                                                  (scan/save-logger-configs! (->> (for [[k v] new-configs]
                                                                                    (when-not (or (when (or (coll? v)
                                                                                                            (string? v)
                                                                                                            (= :api-root k))
                                                                                                    (empty? v))
                                                                                                  (nil? v))
                                                                                      [k v]))
                                                                                  (remove nil?)
                                                                                  (into {}))))
                                                (response-map-fn ctx))}}})))

(def test-api-root
  (resource 
   {:produces [{:media-type u/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type u/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get {:description (str "Test communication with a remote Vigilia server. Will use the default API url or "
                                      "the one provided as a query argument.")
                    :swagger/tags ["Vigilia"]
                    :parameters {:query {(s/optional-key :api-root)
                                         (rs/field s/Str {:description "Alternative API url"})}}
                    :response (fn [ctx]
                                (let [api-root (let [url (some-> ctx :parameters :query :api-root)]
                                                 (if (empty? url)
                                                   scan/default-api-root
                                                   url))]
                                  {:can-connect? (scan/can-connect? api-root)
                                   :api-root api-root}))}}}))

(def test-credentials
  (resource 
   {:produces [{:media-type u/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type u/consumed-types
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :methods {:get {:description (str "Test credentials with a remote Vigilia server. Will use the default "
                                      "currently saved credentials or "
                                      "the one provided as query arguments.")
                    :swagger/tags ["Vigilia"]
                    :parameters {:query {(s/optional-key :project-id) s/Str
                                         (s/optional-key :logger-key) s/Str}}
                    :response (fn [ctx]
                                (let [project-id (some-> ctx :parameters :query :project-id)
                                      logger-key (some-> ctx :parameters :query :logger-key)
                                      credentials (if (and project-id logger-key)
                                                    {:project-id project-id :logger-key logger-key}
                                                    (select-keys (scan/get-logger-configs)
                                                                 [:project-id :logger-key]))]
                                  {:credentials-valid? (scan/credentials-valid? (:project-id credentials)
                                                                                (:logger-key credentials))
                                   :credentials credentials}))}}}))



(def api-route
  ["vigilia" [;["" :none]
              ["/logger" [["" scanning-state]
                          ["/tests" [["/api-root" test-api-root]
                                     ["/credentials" test-credentials]]]
                          ["/configs" configs]
                          ]]]])
