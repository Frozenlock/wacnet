(ns wacnet.views.common
  (:require [hiccup.core :as h]
            [hiccup.page :as hp]
            [hiccup.element :as he]
            [hiccup.form :as hf]))


(defn menu []
  [:div.navbar.navbar-inverse.navbar-fixed-top
   [:div.navbar-inner
    [:div.container-fluid
     [:button.btn.btn-navbar.collapsed {:type "button" :data-toggle "collapse" :data-target ".nav-collapse"}
      [:span.icon-bar][:span.icon-bar][:span.icon-bar]]
     [:span.brand "Wacnet"]
     [:div.nav-collapse.collapse
      [:p.navbar-text.pull-right "Powered by "
       (he/link-to {:class "navbar-link"}"https://bacnethelp.com" "BACnetHelp.com")]
      [:ul.nav
       [:li.active (he/link-to "/" "Home")]
       [:li (he/link-to "/" "About")]
       [:li (he/link-to "/" "Contact")]]]]]])

(defn with-sidebar [{:keys [sidebar body]}]
  [:div.container-fluid
   [:div.row-fluid
    [:div.span3
     [:div.well.sidebar-nav
      [:ul.nav.nav-list
       [:li.nav-header "Devices"]
       (for [i sidebar]
         [:li i])]]]
    [:div.span9 body]]])

(def style
  "  body {
        padding-top: 60px;
        padding-bottom: 40px;
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

(defn layout [& content]
  (hp/html5 {:lang "en"}
            [:head
             [:style style]
             [:title "Wacnet - BACnet network explorer"]
             (hp/include-css "/css/bootstrap.min.css")
             (hp/include-css "/css/bootstrap-responsive.min.css")
             (hp/include-js "/js/jquery-1.9.1.min.js")
             (hp/include-js "/js/bootstrap.min.js")
             ]
            [:body
             (menu)
             content]))