(ns wacnet.routes
  (:require [bidi.bidi :as bidi]))


(def app-routes
  ["/" [["" :home]
        ["repl" :repl]
        ["explorer"
         [["" :devices]
          [["/" :device-id] (bidi/tag :devices :devices-with-id)]
          [["/" :device-id "/" :object-type "/" :object-instance]
           (bidi/tag :devices :devices-with-object)]]]
        ["vigilia"
         [["" :vigilia]]]
        ["configs" :local-device-configs]
        ]])

(def path-for 
  (comp #(str "#" %) (partial bidi/path-for app-routes)))

(defn replace-hash!
  "Replace the current hash"
  [string]
  (js/window.history.replaceState {} nil string))


(defn push-hash!
  "Replace the current hash and insert a new history"
  [string]
  (js/window.history.pushState {} nil string))

(defn goto-hash!
  "Go to specified hash and insert history"
  [string]
  (set! js/window.location.hash string))
