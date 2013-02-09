(ns wacnet.handler
  (:require [compojure.core :refer [routes]]
            [compojure.route :refer [not-found resources]]
            [wacnet.views.explorer :as exp]))





(def handler
  (-> (routes exp/explorer-routes
              (resources "/")
              (not-found "Error 404: not found!"))))

