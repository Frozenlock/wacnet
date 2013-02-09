(ns wacnet.views.explorer
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout with-sidebar]]
            [hiccup.core :as h]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]))

(defroutes explorer-routes

  (GET "/explorer" []
       (layout
        (with-sidebar {:sidebar ["Hello" "this" "is a test"]
                       :body [:div.hero-unit [:h1 "Welcome to your BACnet network!"]
                              "From here you can browse the BACnet network and explore your devices."]})
        (str "Hello there!"))))