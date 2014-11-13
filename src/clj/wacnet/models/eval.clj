(ns wacnet.models.eval
  (:require [clojure.stacktrace :refer [root-cause]]
            [noir.session :as session]
            [wacnet.nrepl :as wnrepl])
  (:import java.io.StringWriter
           (java.util.concurrent TimeoutException TimeUnit FutureTask)))


;; Initialize a poor man's REPL on the web interface

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
  (let [user-session session/*noir-session*
        new-ns (new-namespace)]
    (binding [*ns* new-ns]
      (when init (eval init))
      (remove-ns-future delay))
    (future (Thread/sleep delay)
            (swap! user-session dissoc "ns"))
    new-ns))

(defn make-user-ns []
  (throwable-ns :delay (* 60000 60 24) ;; 24 hours
                :init wnrepl/repl-init))

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
  "Takes a function and an amount of time to wait for thse function to finish
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
                   

(defn get-ns [old]
  (if-let [ns (get old "ns")]
    old
    (assoc old "ns" (make-user-ns))))

(defn eval-request [expr]
  (try
    (eval-string expr (get (session/swap! get-ns) "ns"))
    (catch TimeoutException _
      {:error true :message "Execution Timed Out!"})
    (catch Exception e
      {:error true :message (str (root-cause e))})))
