(ns wacnet.handler
  (:require [compojure.core :refer [routes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [noir.util.middleware :refer [app-handler]]
            [wacnet.ring-utils :refer [wrap-request-bindings]]
            [wacnet.views.explorer :as exp]
            [wacnet.views.home :as home]
            [wacnet.views.repl :as repl]
            [wacnet.views.eval :as eval]
            [wacnet.views.configs :as configs]))


(def handler
  (-> (app-handler [home/home-routes
                    exp/explorer-routes
                    repl/repl-routes
                    eval/eval-routes
                    configs/configs-routes
                    (resources "/")
                    (not-found "Error 404: not found!")])
      wrap-request-bindings
      wrap-stacktrace))
