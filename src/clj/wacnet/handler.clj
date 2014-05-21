(ns wacnet.handler
  (:require [compojure.core :refer [routes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [ring.middleware.params :refer [wrap-params]]
            [noir.util.middleware :refer [app-handler]]
            [wacnet.ring-utils :refer [wrap-request-bindings]]
            [wacnet.views.explorer :as exp]
            [wacnet.views.home :as home]
            [wacnet.views.repl :as repl]
            [wacnet.views.eval :as eval]
            [wacnet.views.configs :as configs]
            [wacnet.views.services :as services]
            [wacnet.api.v1 :as apiv1]
            [liberator.dev :as lib-dev]
            ))


(def handler
  (-> (app-handler [apiv1/api-routes
                    home/home-routes
                    exp/explorer-routes
                    repl/repl-routes
                    eval/eval-routes
                    configs/configs-routes
                    services/logger-routes
                    (resources "/")
                    (not-found "Error 404: not found!")])
      wrap-request-bindings
      wrap-stacktrace
      wrap-params
      (lib-dev/wrap-trace :header :ui);; API dev
      ))
