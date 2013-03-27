(defproject wacnet "1.0.2"
  :description "Webserver to browse a BACnet network"
  :url "https://bacnethelp.com"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [bacure "0.3.15"]
                 [ring "1.1.8"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.2"]
                 [lib-noir "0.3.5"]
                 [org.clojars.frozenlock/logger "2.0.33"]]
  
  :main wacnet.server)

