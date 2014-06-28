(defproject wacnet "1.1.0-BETA"
  :description "Webserver to browse a BACnet network"
  :url "https://hvac.io"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 ;; BACnet
                 [bacure "0.4.12"]

                 ;; Webserver
                 [ring "1.2.1"]
                 [compojure "1.1.6"]
                 [hiccup "1.0.5"]
                 [lib-noir "0.8.3"] ;; sessions

                 ;; API
                 [liberator "0.11.0"]
                 [org.clojure/data.csv "0.1.2"]
                 
                 ;; REPL
                 [org.clojure/tools.nrepl "0.2.3"]
                 [clojure-complete "0.2.3"] ;; necessary for embedded nREPL

                 ;; Misc.
                 [trptcolin/versioneer "0.1.1"]
                 [com.draines/postal "1.11.0"] ;; for email support

                 ;; communication with remote servers
                 [clj-http "0.9.2"]
                 [org.clojars.frozenlock/gzip64 "1.0.0"]

                 ;; recurring jobs
                 [overtone/at-at "1.2.0"]

                 ;;cljs
                 [org.clojure/clojurescript "0.0-2173" :scope "provided"]]

  :profiles {:dev 
             {:dependencies
              [
                 ;;; cljs
               ;[org.clojars.frozenlock/query "0.2.3"]
               [reagent "0.4.2"]
               ;; [cljs-ajax "0.2.3"]
               
               ;; ;; UI 
               [hvacio/hvacio-ui "0.1.3" :exclusions [org.clojure/clojure]]
               ]}}


;  :uberjar-name "wacnet-webserver.jar"
  :min-lein-version "2.0.0"

  :plugins [[lein-cljsbuild "1.0.2"]
            [lein-ring "0.8.10"]]
  :hooks [;leiningen.cljsbuild
          ]
  :source-paths ["src/clj" "src-cross/clj"]
  :cljsbuild {
              :builds
              {:main {
                      :source-paths ["src/cljs" "src-cross/cljs" "src-cross/clj"]
                      :compiler {:output-to "resources/public/js/cljs-min.js"
                                 :optimizations :advanced
                                 :preamble ["reagent/react.min.js"]
                                 :externs ["externs/jquery-1.9.js"]}}
               :dev {
                     :source-paths ["src/cljs"]
                     :compiler {:output-to "resources/public/js/out-dev/cljs.js"
                                
                                :output-dir "resources/public/js/out-dev"
                                ;:source-map-path "js/out-dev"
                                :source-map "resources/public/js/out-dev/cljs.js.map"


                                :preamble ["reagent/react.js"]
                                :optimizations :simple
                                :pretty-print true}}}}

  :ring {:handler wacnet.handler/handler :port 3000}
  :main wacnet.server)

