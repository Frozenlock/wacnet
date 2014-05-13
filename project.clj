(defproject wacnet "1.0.6"
  :description "Webserver to browse a BACnet network"
  :url "https://hvac.io"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [bacure "0.3.20"]
                 [ring "1.1.8"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.2"]
                 [lib-noir "0.3.5"]
                 [org.clojars.frozenlock/logger "2.0.35"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [clojure-complete "0.2.3"] ;; clojure-complete is necessary for embedded nREPL
                 [trptcolin/versioneer "0.1.1"]]
  :main wacnet.server)

