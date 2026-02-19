(ns wacnet.systray
  (:require [clojure.java.browse])
  (:import (java.awt.event ActionListener)
           (dorkbox.systemTray Menu
                               MenuItem
                               Separator
                               SystemTray)))

(defn- menu-items [port]
  [["Open in browser" #(clojure.java.browse/browse-url (str "http://localhost:" port))]
   [:separator]
   ["Quit Wacnet" #(System/exit 0)]])

(defn config-menu! [systray items]
  (when-let [menu (.getMenu systray)]
    (doseq [item items]
      (let [string-or-key (first item)
            func          (last item)
            item (if (= string-or-key :separator)
                   ;; separator
                   (Separator.)
                   ;; menu item
                   (let [listener (proxy [ActionListener] []
                                    (actionPerformed [evt] (func)))]
                     (MenuItem. string-or-key listener)))]
        (.add menu item)))))

(defn initialize-systray!
  ([] (initialize-systray! 47800))
  ([port]
   (when-let [systray (SystemTray/get)]
     (let [image (clojure.java.io/resource "public/img/favicon.png")]
       (doto systray
         (.setImage image)
         (.setTooltip "Wacnet")
         (.setStatus "Wacnet"))
       (config-menu! systray (menu-items port))))))
