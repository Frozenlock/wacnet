(defproject wacnet "2.1.6-alpha4"
  :description "Webserver to browse a BACnet network"
  :url "https://hvac.io"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  ;; WARNING
  ;; compiling with java 9 causes the webserver to hang when started on java 8
  :dependencies [[org.clojure/clojure "1.9.0"]

                 ;; BACnet
                 [bacure "1.0.8"]

                 [io.hvac.vigilia/vigilia-logger "1.0.14-alpha"]

                 ;; webserver stuff
                 [bidi "2.1.3"] ; routing
                 [aleph "0.4.6"] ; server
                 [aleph-middleware "0.2.0"]
                 [yada "1.2.13"]
                 
                 [trptcolin/versioneer "0.2.0"]

                 ;; error logs
                 [org.slf4j/slf4j-nop "1.7.25"]
                 

                 ;; nREPL
                 [nrepl "0.6.0"]
                 [cider/cider-nrepl "0.21.1"]

                 ;; cljs
                 [org.clojure/clojurescript "1.10.339"]
                 [org.clojure/core.async "0.4.474"]
                 [org.clojure/tools.reader "1.3.0"]
                 [cljsjs/resize-observer-polyfill "1.4.2-0"]

                 [reagent "0.8.1"] ;; [reagent "0.8.0-alpha2"] ;; doesn't work with clojure 1.9 on java 9
                 
                 [org.clojars.frozenlock/reagent-modals "0.2.8"]
                 [org.clojars.frozenlock/reagent-keybindings "1.0.2"]
                 [cljs-ajax "0.7.4"]
                 [re-com "2.1.0" :exclusions [reagent]]
                 [com.andrewmcveigh/cljs-time "0.5.2"]
                 [cljsjs/fixed-data-table-2 "0.7.17-2" ;; 0.8.11 has problems with 'scroll-to-row'
                  :exclusions [cljsjs/react]]]

  :plugins [[lein-environ "1.1.0"]
            [lein-ancient "0.6.15"]
            [lein-cljsbuild "1.1.7"]
            [org.clojars.rasom/lein-externs "0.1.7"]]

  :javac-options ["-target" "1.8" "-source" "1.8"] ;; retest later
  
  :manifest {"SplashScreen-Image" "public/img/splash.png"}

  :min-lein-version "2.5.0"
  
  :main wacnet.server

  :clean-targets ^{:protect false} [:target-path
                                    [:cljsbuild :builds :app :compiler :output-dir]
                                    [:cljsbuild :builds :app :compiler :output-to]]

  :cljsbuild
  {:builds {:min
            {:source-paths ["src/cljs" "src/cljc" "env/prod/cljs"]
             :compiler
             {:output-to        "target/cljsbuild/public/js/app.js"
              :output-dir       "target/cljsbuild/public/js"
              :source-map       "target/cljsbuild/public/js/app.js.map"
              :optimizations :advanced
              :pretty-print  false
              :externs ["externs.js"]}}
            :app
            {:source-paths ["src/cljs" "src/cljc" "env/dev/cljs"]
             :figwheel {:on-jsload "wacnet.core/init!"}
             :compiler
             {:main "wacnet.dev"
              :asset-path "/js/out"
              :output-to "target/cljsbuild/public/js/app.js"
              :output-dir "target/cljsbuild/public/js/out"
              :source-map true
              :optimizations :none
              :pretty-print  true}}}}

  :resource-paths ["resources" "target/cljsbuild"]

  :profiles {:dev {;:repl-options {:init-ns wacnet.repl}

                   :dependencies [[ring/ring-mock "0.3.2"]
                                  [ring/ring-devel "1.6.3"]
                                  [figwheel-sidecar "0.5.18"]
                                  [nrepl "0.6.0"]
                                  [cider/piggieback "0.4.0"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.5.18"]
                             [cider/cider-nrepl "0.21.1"]]

                   :figwheel {:http-server-root "public"
                              :readline false
                              :server-port 3449
                              :nrepl-port 7002
                              :nrepl-middleware ["cider.piggieback/wrap-cljs-repl"
                                                 "cider.nrepl/cider-middleware"]
                              :css-dirs ["resources/public/css"]
                              :ring-handler wacnet.handler/app}

                   :env {:dev true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {:main "wacnet.dev"
                                                         :source-map true}}}}}

             :uberjar {:source-paths ["env/prod/clj"]
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]
                       :env {:production true}
                       :aot :all
                       :omit-source true}}
  
  :source-paths ["src/clj" "src/cljs" "src/cljc"])
