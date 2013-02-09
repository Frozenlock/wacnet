(defproject wacnet "0.1.0-SNAPSHOT"
  :description "Webserver to browse a BACnet network"
  :url "https://bacnethelp.com"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [bacure "0.1.0"]
                 [ring "1.1.8"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.2"]]
  ;:plugins [[lein-getdown "0.1.0-SNAPSHOT"]]
  ;:getdown {:appbase "https://bacnethelp.com/some-url/"}
  :main wacnet.server)

