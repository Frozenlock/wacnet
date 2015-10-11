(defproject wacnet "1.1.8"
  :description "Webserver to browse a BACnet network"
  :url "https://hvac.io"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]
                 [org.clojure/clojurescript "0.0-2173" :scope "provided"]
                 
                 
                 ;; BACnet
                 [bacure "0.5.5"]

                 ;; Webserver
                 [ring "1.2.1"]
                 [compojure "1.1.8"]
                 [hiccup "1.0.5"]
                 [lib-noir "0.8.3"] ;; sessions

                 ;; API
                 [liberator "0.11.0"]
                 [org.clojure/data.csv "0.1.2"]
                 
                 ;; REPL
                 ^:replace [org.clojure/tools.nrepl "0.2.10"]
                 [cider/cider-nrepl "0.9.1"]

                 ;; Misc.
                 [trptcolin/versioneer "0.1.1"]
                 [com.draines/postal "1.11.0"] ;; for email support

                 ;; logging 
                 [io.hvac.vigilia/vigilia-logger "1.0.4"]

                 ;; recurring jobs
                 

                 [overtone/at-at "1.2.0"]
                 
                 ;; ;; UI 
                 [hvacio/hvacio-ui "0.1.13"]
                 [org.clojars.frozenlock/reagent-modals "0.2.0"]
                 [reagent "0.4.2"]

                 [com.taoensso/tower "3.0.2"]]

  :profiles {:uberjar {:aot :all}}

  :manifest {"SplashScreen-Image" "public/img/splash.png"}


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
                      :source-paths ["src/cljs"]
                      :compiler {:output-to "resources/public/js/cljs-min.js"
                                 :optimizations :advanced
                                 :preamble ["reagent/react.min.js"]
                                 :externs ["externs/jquery-1.9.js"
                                           "hvacio-ui-resources/public/js/nprogress.js"]
                                 :closure-warnings {:externs-validation :off
                                                    :non-standard-jsdoc :off}}}
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

