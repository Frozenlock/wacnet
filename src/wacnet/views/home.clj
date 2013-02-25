(ns wacnet.views.home
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout]]
            [hiccup.core :as h]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]))

(defmacro get-wacnet-version []
  (let [x# (System/getProperty "wacnet.version")]
    `~x#))

(def center "float: none; margin-left: auto; margin-right: auto;")

(defroutes home-routes

  (GET "/" []
       (layout
        [:div.container
         [:div.hero-unit [:h1 "Wacnet: a BACnet webserver"]
          [:h2 "But also a portable BACnet toolkit!"]
          [:ul
           [:li "Easy to use - Can run on any computer with JAVA from a USB flash drive!"]
           [:li "Made with bootstrap: tablet and cellphone ready!"]
           [:li (he/link-to "https://github.com/Frozenlock/wacnet" "Free and open source!")]]]
         [:div.row-fluid
          [:div.span4 "This software is under development. "
           "You might see some errors. If it's the case, please let us know!"]
          [:div.span8 (he/link-to "/explorer"
                                  [:div.btn.btn-primary
                                   "Understood, show me the network!"])]]
         [:span.label.label-warning (get-wacnet-version)]])))