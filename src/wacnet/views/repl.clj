(ns wacnet.views.repl
  (:require [compojure.core :refer [defroutes GET]]
            [wacnet.views.common :refer [layout]]
            [hiccup.page :as hp]
            [hiccup.element :as he]))

;; Almost everything for the repl comes from the tryclj.com website,
;; so all credits should go to Anthony Grimes & cie.

(defroutes repl-routes
  (GET "/repl" []
       (layout
        (hp/include-js "/js/jquery-console/jquery-1.4.2.min.js"
                       "/js/jquery-console/jquery.console.js")
        (hp/include-js "/js/tryclojure.js")
        (hp/include-css "/css/tryclojure.css")
        [:div.container
         [:div#header
          [:h1
           (he/link-to "http://www.clojure.org" [:span.logo-clojure "Clo" [:em "j"] "ure"]) " "
           [:span.logo-try "REPL"]]]
         [:div#container
          [:pre [:div#console.console]]]

         [:div.row-fluid
          [:div.span6
           [:h2 "REPL?"]
           [:p
            "REPL means "[:i "Read Eval Print Loop."]" "
            "It's an interactive development environment. "
            "With this, you can define new functions and use them on the spot, "
            "without the need to recompile!"]
           [:p
            "Try it! Define a new function by typing "[:code "(defn square [x] (* x x))"]
            " and then pressing ENTER. Now use your new function: "
            [:code "(square 10)"]]]

          [:div.span6
           [:h2 "Power user!"]
           [:p
            "We decided to give you the same power that we have: "
            "You have access to the same functions than those on "
            "which this webserver is built!"]

           [:p "Have some \"special\" needs? Write your own custom function and take "
            "control of the BACnet network!"]

           [:p "Be sure to check the "
            (he/link-to "http://frozenlock.github.com/bacure/bacure.core.html" "documentation")"!"]]]

         [:div.row-fluid
          [:div.span12
           [:h3 "Examples"]
           [:p
            [:h4 "List of remote devices"]
            "This one is simple enough: "
            [:script {:src "https://gist.github.com/Frozenlock/3ce34c61a5b995d5213d.js"}]]
           [:p
            [:h4 "Properties values"]
            "This will retrieve the value of the properties "[:code ":present-value"]" and "
            [:code ":description"]" :"
            [:script {:src "https://gist.github.com/Frozenlock/69e45162d0008fc7982c.js"}]]
           [:p
            [:h4 "Filter objects"]
            "You might want to filter objects base on their properties. "
            "Quite hard to do with with a simple webpage interface, but effortless by using "
            "this command: "
            [:script {:src "https://gist.github.com/Frozenlock/41ee57a1a7a4b0cd5f1a.js"}]]
           [:p
            [:h4 "Export results"]
            "Found something interesting? Easily export it for later analysis: "
            [:script {:src "https://gist.github.com/Frozenlock/345578e4aecc770482b2.js"}]]]]
         [:div.navbar.navbar-fixed-bottom {:style "background-color: white;"}
          [:div "The REPL functions will expire after 24 hours. Function timeout: 5 minutes."]]])))