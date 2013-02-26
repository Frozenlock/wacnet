(defproject wacnet "0.1.3-BETA"
  :description "Webserver to browse a BACnet network"
  :url "https://bacnethelp.com"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.5.0-RC16"]
                 [bacure "0.1.16"]
                 [ring "1.1.8"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.2"]
                 [clojail "1.0.3"]
                 [lib-noir "0.3.5"]]
  
  :main wacnet.server)

