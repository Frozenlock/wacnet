(ns wacnet.ring-utils)


(def ^{:dynamic true} *url*         nil) ; url of current request
(def ^{:dynamic true} *http-scheme* nil) ; keyword, :http or :https
(def ^{:dynamic true} *req*         nil) ; params of current request


(defn wrap-request-bindings [handler]
  (fn [req]
    (binding [*url* (:uri req)
              *http-scheme* (:scheme req)
              *req* req]
      (handler req))))
