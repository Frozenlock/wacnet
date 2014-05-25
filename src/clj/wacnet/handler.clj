(ns wacnet.handler
  (:require [compojure.core :refer [routes] :as comp]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [compojure.handler :as handler]
            [wacnet.ring-utils :refer [wrap-request-bindings]]
            [wacnet.views.explorer :as exp]
            [wacnet.views.home :as home]
            ;; [wacnet.views.repl :as repl]
            ;; [wacnet.views.eval :as eval]
            [wacnet.views.configs :as configs]
            ;; [wacnet.views.services :as services]
            [wacnet.api.v1 :as apiv1]))

(comp/defroutes app-routes
  apiv1/api-routes
  home/home-routes
  exp/explorer-routes
  ;; repl/repl-routes
  ;; eval/eval-routes
  configs/configs-routes
  ;; services/logger-routes
  (resources "/")
  (not-found "Error 404: not found!"))


(def handler
  (-> app-routes
      (handler/site) ;; some default middleware
      (wrap-stacktrace)
      (wrap-request-bindings)))
