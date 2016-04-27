(ns wacnet.api.util)

(def produced-types
  #{"application/transit+json"
    "application/transit+msgpack"
    "application/json" 
    "application/edn" 
    "text/html"})


(def consumed-types
  #{"application/transit+json"
    "application/transit+msgpack"
    "application/json" 
    "application/edn"})

(defn make-link
  "Given a request context, return the full URL (with scheme and host)
  for the current request, or for a given path."
  ([ctx] (make-link ctx (:uri (:request ctx))))
  ([ctx path]
   (str (name (get-in ctx [:request :scheme]))
        "://"
        (or (get-in ctx [:request :headers "host"]) ;; for tests
            (get-in ctx [:request :server-name]))
        path)))
