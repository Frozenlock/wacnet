(ns wacnet.handler
  (:require [bidi.ring :refer [make-handler] :as bidi]
            [wacnet.api :as api]
            [hiccup.page :as hp :refer [html5 include-css include-js]]
            [yada.yada :as yada :refer [yada resource]]
            [aleph.middleware.content-type :refer [wrap-content-type]]
            [aleph.middleware.session :refer [wrap-session]]
            [trptcolin.versioneer.core :as version]))

(def mount-target
  [:div#app {:style "width:100%;height:100%"}
   [:div {:style "margin:10px;margin-bottom:none;"} 
    [:h4 "Loading javascript application..."]
    [:p "If for any reason your browser can't load the application, you can still view your BACnet network using the API."
     [:ul 
      [:li [:a {:href "/api/v1"} "Swagger UI for Wacnet API"]]
      [:li [:a {:href "/api/v1/devices"} "Raw API"]]]]]])

(def loading-page
  (html5
   [:head
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
    [:link {:rel "shortcut icon" :href "/img/favicon.png"}]
    (include-css "/css/site.css")

     ;;; font awesome
    (include-css "/font-awesome/css/font-awesome.min.css")     

     ;;; bootstrap
    (include-css "/bootstrap-3.3.6-dist/css/bootstrap.min.css")
    (include-css "/bootstrap-3.3.6-dist/css/bootstrap-theme.min.css")
    
    (include-css "/css/material-design-iconic-font.min.css")
    (include-css "/css/re-com.css")
    (include-css "/css/fixed-data-table.min.css")
    
    (include-js "/bootstrap-3.3.6-dist/js/jquery-2.2.0.min.js")
    (include-js "/bootstrap-3.3.6-dist/js/bootstrap.min.js")
    [:script {:type "text/javascript"}
     (str "var WacnetVersion = \"" (version/get-version "wacnet" "wacnet") "\"")]]
   
   [:body
    mount-target
    (include-js "/js/app.js")
    ]))

(def loading-page-resource
  (resource {:produces "text/html"
             :methods {:get {:response (fn [ctx]
                                         loading-page)}}}))

(def routes
  [""
   [["/" loading-page-resource]
    (api/make-api-routes "/api/v1")
    ;; ["/js" (bidi/->ResourcesMaybe {:prefix "public/js/"})]
    ;; ["/css" (bidi/->ResourcesMaybe {:prefix "public/css/"})]
    ;; ["/img" (bidi/->ResourcesMaybe {:prefix "public/img/"})]
    ["/" (bidi/->ResourcesMaybe {:prefix "public/"})]
    ["/" (bidi/->ResourcesMaybe {:prefix "public/web-nrepl/"})]
    ;; 404 if nothing is found
    [true (fn [req] {:status 404 :body "404 not found"})]]])


(def handler 
  (-> (make-handler routes)
      (wrap-content-type)
      ;; (wrap-session)
      ))

(comment
  (def server
    (aleph.http/start-server handler {:port 3449})))


;;; dev functions

(defn wrap-deref [h]
  (fn [req]
    (let [result (h req)]
      (if (instance? clojure.lang.IDeref result)
        @result
        result))))

(def app
  (wrap-deref handler))
