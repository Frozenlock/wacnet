(ns wacnet.api.repl
  (:require [clojure.stacktrace :refer [root-cause]]
            [wacnet.api.bacnet.common :as co]
            [wacnet.nrepl :as wnrepl]
            [yada.resource :refer [resource]]
            [schema.core :as s])
  (:import java.io.StringWriter
           [java.util.concurrent TimeoutException TimeUnit FutureTask]))

(def users-namespaces (atom {}))

(defn remove-ns-future
  "Unmap a namespace after `delay' in ms."
  [delay]
  (future (Thread/sleep delay)  ;24 hours (* 60000 60 24)
          (-> *ns* .getName remove-ns)))

(defmacro new-namespace
  "Create a new newspace and return it." []
  (let [ns-name# (symbol (gensym "user-"))]
    `(binding [*ns* *ns*]
       (ns ~ns-name#)
       *ns*)))

(defn throwable-ns
  "Creates a new ns that will expire after `delay' in ms. If init is
  provided, it will be evaluated when the namespace is created."
  [&{:keys [delay init]}]
  (let [new-ns (new-namespace)]
    (binding [*ns* new-ns]
      (when init (eval init))
      (remove-ns-future delay))
    new-ns))

(defn make-user-ns! [session-id]
  (let [delay-ms (* 60000 60 24) ;; 24 hours
        ns (throwable-ns :delay delay-ms
                         :init wnrepl/repl-init)]
    (swap! users-namespaces assoc session-id ns)
    (future (Thread/sleep delay-ms)
            (swap! users-namespaces dissoc session-id))
    ns))


;; from clojail
(def ^{:doc "Create a map of pretty keywords to ugly TimeUnits"}
  uglify-time-unit
  (into {} (for [[enum aliases] {TimeUnit/NANOSECONDS [:ns :nanoseconds]
                                 TimeUnit/MICROSECONDS [:us :microseconds]
                                 TimeUnit/MILLISECONDS [:ms :milliseconds]
                                 TimeUnit/SECONDS [:s :sec :seconds]}
                 alias aliases]
             {alias enum})))

(defn thunk-timeout
  "Takes a function and an amount of time to wait for those function to finish
   executing. The sandbox can do this for you. unit is any of :ns, :us, :ms,
   or :s which correspond to TimeUnit/NANOSECONDS, MICROSECONDS, MILLISECONDS,
   and SECONDS respectively."
  ([thunk ms]
     (thunk-timeout thunk ms :ms nil)) ; Default to milliseconds, because that's pretty common.
  ([thunk time unit]
     (thunk-timeout thunk time unit nil))
  ([thunk time unit tg]
     (let [task (FutureTask. thunk)
           thr (if tg (Thread. tg task) (Thread. task))]
       (try
         (.start thr)
         (.get task time (or (uglify-time-unit unit) unit))
         (catch TimeoutException e
           (.cancel task true)
           (.stop thr) 
           (throw (TimeoutException. "Execution timed out.")))
         (catch Exception e
           (.cancel task true)
           (.stop thr) 
           (throw e))
         (finally (when tg (.stop tg)))))))


(defn eval-form [form namespace]
  (with-open [out (StringWriter.)]
    (binding [*out* out
              *ns* namespace]
      (let [result (eval form)]
        {:expr form
         :result [out result]}))))

(defn eval-string [expr namespace]
  (let [form (binding [*read-eval* false] (read-string expr))]
    (thunk-timeout #(eval-form form namespace) (* 60000 5))))
                   

(defn get-ns!
  "Return a namespace associated with the given session. If nothing is
  found, initiate the namespace."
  [session-id]
  (or (get @users-namespaces session-id)
      (make-user-ns! session-id)))

(defn eval-request 
  ([expr] (eval-request expr "web-repl"))
  ([expr session-id]
   (try
     (eval-string expr (get-ns! session-id))
     (catch TimeoutException _
       {:error true :message "Execution Timed Out!"})
     (catch Exception e
       {:error true :message (str (root-cause e))}))))


(def eval-resource
  (resource
   {:produces [{:media-type co/produced-types
                :charset "UTF-8"}]
    :consumes [{:media-type #{}
                :charset "UTF-8"}]
    :access-control {:allow-origin "*"}
    :swagger/tags ["REPL"]
    :methods {:get {:summary "Devices list"
                    :parameters {:query {:expr s/Str}}
                    :description "Evaluate the given expression in a Clojure repl."
                    :response (fn [ctx]
                                (let [session (some-> ctx :request :session)
                                      expr (some-> ctx :parameters :query :expr)
                                      {:keys [expr result error message] :as res} (eval-request expr session)
                                      data (if error
                                             res
                                             (let [[out res] result]
                                               {:expr (pr-str expr)
                                                :result (str out (pr-str res))}))]
                                  data))}}}))
