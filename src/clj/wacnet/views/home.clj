(ns wacnet.views.home
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout]]
            [hiccup.core :as h]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [trptcolin.versioneer.core :as version]))

(defn get-wacnet-version []
  (version/get-version "wacnet" "wacnet"))

(def center "float: none; margin-left: auto; margin-right: auto;")

(defroutes home-routes

  (GET "/" []
       (layout
        [:div.container
         [:div.jumbotron [:h1 "Wacnet: a BACnet webserver"]
          [:h2 "But also a portable BACnet toolkit!"]
          [:ul
           [:li "Easy to use - Can run on any computer with JAVA from a USB flash drive!"]
           [:li "Made with bootstrap: tablet and cellphone ready!"]
           [:li (he/link-to "https://github.com/Frozenlock/wacnet" "Free and open source!")]]]
         [:div.row
          [:div.col-md-4 "This software is free to use. In exchange, if you see some errors, "
           "please let us know!"]
          [:div.col-md-8 (he/link-to "/explorer"
                                  [:div.btn.btn-primary
                                   "Understood, show me the network!"])]]
         [:span.label.label-warning (get-wacnet-version)]
         [:div.row
          [:p [:h4 "Messages:"]
           [:script {:src "https://gist.github.com/Frozenlock/8406e487ecc70ee204d0.js"}]]]])))
