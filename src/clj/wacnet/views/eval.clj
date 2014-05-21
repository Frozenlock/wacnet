(ns wacnet.views.eval
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.models.eval :refer [eval-request]]
            [noir.response :as resp]))

(defroutes eval-routes

  (GET "/eval.json" [expr jsonp]
       (let [{:keys [expr result error message] :as res} (eval-request expr)
             data (if error
                    res
                    (let [[out res] result]
                      {:expr (pr-str expr)
                       :result (str out (pr-str res))}))]
         (if jsonp
           (resp/jsonp jsonp data)
           (resp/json data)))))