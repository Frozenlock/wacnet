(ns wacnet.handler
  (:require [compojure.core :refer [routes]]
            [compojure.route :refer [not-found resources]]
            [ring.middleware.stacktrace :refer [wrap-stacktrace]]
            [wacnet.ring-utils :refer [wrap-request-bindings]]
            [wacnet.views.explorer :as exp]
            [wacnet.views.home :as home]))


(def handler
  (-> (routes home/home-routes
              exp/explorer-routes
              (resources "/")
              (not-found "Error 404: not found!"))
      wrap-request-bindings
      wrap-stacktrace
      ))

