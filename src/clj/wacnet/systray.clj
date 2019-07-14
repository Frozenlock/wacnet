(ns wacnet.systray
  (:require [clojure.java.browse])
  (:import (java.awt.event ActionListener)
           (dorkbox.systemTray Menu
                               MenuItem
                               Separator
                               SystemTray)))

(def menu-items [["Open in browser" #(clojure.java.browse/browse-url "http://localhost:47800")]
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

(defn initialize-systray! []
  (when-let [systray (SystemTray/get)]
    (let [title "Wacnet"
          image (clojure.java.io/resource "public/img/favicon.png")]
      ;; basic system tray configs
      (doto systray
        (.setImage image)
        (.setTooltip "Wacnet")
        (.setStatus "Wacnet"))

      ;; the menu (what is shown when we click the icon)
      (config-menu! systray menu-items))))
