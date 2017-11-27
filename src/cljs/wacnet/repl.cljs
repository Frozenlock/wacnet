(ns wacnet.repl
  (:require [reagent.core :as r]
            [ajax.core :refer [GET POST]]
            [re-com.core :as re]
            [wacnet.templates.common :as common]
            [goog.net.jsloader]))

;; (defn gist [gist-id]
;;   (let [data (r/atom nil)
;;         fetch-fn (fn []
;;                    (GET (str "https://gist.github.com/" gist-id ".json&callback=callback")
;;                        :handler (fn [resp] (reset! data resp))))]
;;     (r/create-class
;;      {:display-name "Gist"
;;       :component-did-mount fetch-fn
;;       :reagent-render 
;;       (fn [gist-id]
;;         [:div (str @data)])})))


(defn gist [gist-id]
  (let [is-loaded? (fn [iframe]
                     (-> (aget iframe "contentWindow" "document" "body") 
                         (.getElementsByTagName "div")
                         (array-seq)
                         (first)
                         (nil?)
                         (not)))
        resize-iframe (fn [iframe]
                        (let [height (aget iframe "contentWindow" "document" "body" "scrollHeight")]
                          (aset iframe "height" (str height "px"))))
        forget-me? (atom nil)
        when-loaded (fn when-loaded [iframe this-fn]
                      (when-not @forget-me?
                        (if (is-loaded? iframe)
                          (this-fn)
                          (js/setTimeout #(when-loaded iframe this-fn) 1000))))]
    (r/create-class
     {:component-did-mount (fn [this]
                             (let [iframe (r/dom-node this)
                                   doc (.-contentDocument iframe)
                                   script (str "<script type='text/javascript' src='https://gist.github.com/"
                                               gist-id ".js'></script>")
                                   iframe-html (str "<html><head><base target=\"_blank\"></head><body "
                                                    ">"script "</body></html>")]
                               (doto doc
                                 (.open)
                                 (.write iframe-html)
                                 (.close))
                               (when-loaded iframe #(resize-iframe iframe))))
      :component-will-unmount #(reset! forget-me? true)
      :reagent-render
      (fn [gist-id]
        [:iframe {:width "100%"
                  :frame-border 0}])})))

(defn repl-page []
  (r/create-class
   {:component-did-mount (fn [this]
                           (goog.net.jsloader.safeLoadMany
                            (clj->js
                             [(-> (goog.string.Const.from "/jquery-console/jquery.console.js")
                                  (goog.html.TrustedResourceUrl.fromConstant))
                              (-> (goog.string.Const.from "/tryclojure.js")
                                  (goog.html.TrustedResourceUrl.fromConstant))])))
    :reagent-render
    (fn []      
      [common/scrollable 
       [:div.container
        [:link {:href "/tryclojure.css" :rel "stylesheet" :type "text/css"}]
        [:div [:div#header
               [:h1
                [:img {:src "/img/clojure-logo.png" :style {:height "1em" :margin-bottom "5px"}}]
                [:a {:href "http://www.clojure.org"} [:span.logo-clojure "Clo" [:em "j"] "ure"]]
                [:span.logo-try " REPL "]]]
         [:pre [:div#console.console]]]
        [:div.row
         [:div.col-sm-6.col-sm-offset-3
          [:h2 "REPL?"]
          [:p
           "REPL means "[:i "Read Eval Print Loop."]" "
           "It's an interactive development environment. "
           "With this, you can define new functions and use them on the spot, "
           "without the need to recompile!"]
          [:p
           "Try it! Define a new function by typing "[:code "(defn square [x] (* x x))"]
           " and then pressing ENTER. Now use your new function: "
           [:code "(square 10)"]]

          [:i
           "Want a better experience? Use Emacs with "
           [:a {:href "https://github.com/clojure-emacs/cider/blob/master/README.md"}
            "cider"]
           " (or any other nrepl interface) "
           "and connect to port 47999!"]]]
        [:div.row
         [:div.col-sm-12
          [:h3 "Examples"]
          [:p
           "Check the "[:a {:href "https://wiki.hvac.io/doku.php?id=suppliers:hvac.io:wacnet#scripts"}
                        "Wiki"]
           " to see scripts made by users."]
          [:h4 "List of remote devices"]
          [:p      
           "This one is simple enough: "
           [gist "Frozenlock/3ce34c61a5b995d5213d"]]
          [:h4 "Properties values"]
          [:p
           "This will retrieve the value of the properties "[:code ":present-value"]" and "
           [:code ":description"]" :"
           [gist "Frozenlock/69e45162d0008fc7982c"]
           ]
          [:h4 "Filter objects"]
          [:p
           "You might want to filter objects base on their properties. "
           "Quite hard to do with with a simple webpage interface, but effortless by using "
           "this command: "
           [gist "Frozenlock/41ee57a1a7a4b0cd5f1a"]
           ]
          [:h4 "Export results"]
          [:p
           "Found something interesting? Easily export it for later analysis: "
           [gist "Frozenlock/345578e4aecc770482b2"]
           ]]]]])}))
