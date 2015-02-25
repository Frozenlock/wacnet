(ns wacnet.systray
  (:require [clojure.java.browse])
  (:import java.awt.SystemTray
           java.awt.TrayIcon
           (java.awt.event ActionListener)))

(defn is-supported? []
  (SystemTray/isSupported))

(defn get-system-tray []
  (SystemTray/getSystemTray))

(defn create-tray-icon
  ([path] (create-tray-icon path nil))
  ([path description] (create-tray-icon path nil nil))
  ([path description popup-menu]
   (doto (-> (java.awt.Toolkit/getDefaultToolkit)
             (.getImage (clojure.java.io/resource path))
             (TrayIcon. description))
     (.setImageAutoSize true)
     (.setPopupMenu popup-menu))))

(defn clear-tray! []
  (let [tray-icons (-> (get-system-tray)
                       (.getTrayIcons))]
    (doseq [icon tray-icons]
      (-> (get-system-tray)
          (.remove icon)))))

(defn create-popup-menu [items]
  (let [popup-menu (java.awt.PopupMenu.)]
    (doseq [item items]
      (let [string-or-key (first item)
            func (last item)]
        (if (= string-or-key :separator)
          (.addSeparator popup-menu)
          (let [menu-item (java.awt.MenuItem. string-or-key)]
            (.addActionListener menu-item
                                (proxy [ActionListener] []
                                  (actionPerformed [evt] (func))))
            (.add popup-menu menu-item)))))
    popup-menu))

(def menu-items [["Exit" #(System/exit 0)]
                 [:separator]
                 ["Open in browser" #(clojure.java.browse/browse-url "http://localhost:47800")]])


(defn initialize-systray! []
  (when (is-supported?)
    (-> (get-system-tray)
        (.add (create-tray-icon "public/img/favicon.png" "Wacnet" (create-popup-menu menu-items))))))
