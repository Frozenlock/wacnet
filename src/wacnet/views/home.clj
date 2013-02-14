(ns wacnet.views.home
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout with-sidebar]]
            [hiccup.core :as h]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]))

(def center "float: none; margin-left: auto; margin-right: auto;")

(defroutes home-routes

  (GET "/" []
       (layout
        [:div.container
         [:div.hero-unit [:h1 "Wacnet: a BACnet webserver"]
          [:h2 "(... and hopefully much more, soon!)"]
          [:ul
           [:li "Easy to use - Can run on any computer with JAVA from a USB key!"]
           [:li "Made with bootstrap: tablet and cellphone ready!"]
           [:li (he/link-to "https://github.com/Frozenlock/wacnet" "Free and open source!")]]]
         [:div.row-fluid
          [:div.span6 "Please keep in mind that this software isn't complete yet. "
           "You still can't send any commands to the network (such as changing values). "
           "You might also see some errors when trying to see 'unknown' properties. "
           "If it's the case, please let us know!"]
          [:div.span6 (he/link-to "/explorer"
                                  [:div.btn.btn-primary
                                   "Understood, show me the network!"])]]])))