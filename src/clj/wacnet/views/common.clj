(ns wacnet.views.common
  (:require [hiccup.core :as h]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [wacnet.ring-utils :refer [*url*]]
            [wacnet.views.templates :as tp]
            [noir.session :as session]))


(defn menu-item [link content]
  [:li {:class (when (re-find (re-pattern (str "^" link)) *url*) "active")} (he/link-to link content)])

(defn navbar-header []
  [:div.navbar-header
   [:button.navbar-toggle {:type "button" :data-toggle "collapse" :data-target "#main-navbar"}
    [:span.sr-only "Toggle navigation"]
    (repeat 3 [:span.icon-bar])]
   [:a.navbar-brand {:href "/"} "Wacnet"]])

  
(defn navbar-content []
  [:div.collapse.navbar-collapse {:id "main-navbar"}
   [:ul.nav.navbar-nav
    (menu-item "/explorer" "Explorer")
    (menu-item "/configs" "BACnet Configs")
    (menu-item "/repl" "REPL")
    (menu-item "/vigilia/configs" "Vigilia configs")]
   [:a.navbar-brand.navbar-right {:href "https://hvac.io"}
    "Powered by "[:img.img-rounded {:src "/img/HVACIO-logo.svg"
                       :style "height: 23px;"}]]])

(defn menu []
  [:div.nav.navbar-default.navbar-fixed-top {:role "navigation"}
   [:div
    (navbar-header)
    (navbar-content)]])


(defn with-sidebar [{:keys [header sidebar body]}]
  `[:div.container-fluid
    [:div.row-fluid
     [:div.span3
      [:div.well.sidebar-nav
       [:ul.nav.nav-list
        [:li.nav-header ~header]
        ~@sidebar]]]
     [:div.span9 ~body]]])

(def style
  "  body {
     padding-top: 50px;
      }
      .sidebar-nav {
        padding: 9px 0;
      }

      @media (max-width: 980px) {
        /* Enable use of floated navbar text */
        .navbar-text.pull-right {
          float: none;
          padding-left: 5px;
          padding-right: 5px;
        }
      }")

(defn message
  "When a message is present in the session, creates a DIV and show it
  to the user."[]
  (when-let [mess (session/get :msg)]
    (session/put! :msg nil)
    [:div.alert.alert-warning.alert-dismissable.message.text-center
     [:button {:type "button" :class "close" :data-dismiss "alert" :aria-hidden "true"}"&times;"]
     [:strong mess]]))

(defn layout [& content]
  (hp/html5 {:lang "en"}
            [:head
             [:link {:rel "shortcut icon" :href "/img/favicon.png"}]
             [:style style]
             [:title "Wacnet - BACnet network explorer"]
             (hp/include-css "/css/bootstrap.min.css")
             (hp/include-css "/css/font-awesome.min.css")
             (hp/include-js "/js/jquery-1.11.1.min.js")
             (hp/include-js "/js/bootstrap.min.js")
             (hp/include-js ;"/js/out-dev/cljs.js" 
                            "/js/cljs-min.js"
                            )

             ]
            [:body
             (menu)
             (message)
             content]))
