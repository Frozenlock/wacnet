(defproject wacnet "1.1.0"
  :description "Webserver to browse a BACnet network"
  :url "https://hvac.io"
  :license {:name "GNU General Public License V3"
            :url "http://www.gnu.org/licenses/gpl-3.0.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [bacure "0.3.20"]
                 [ring "1.1.8"]
                 [compojure "1.1.5"]
                 [hiccup "1.0.2"]
                 [lib-noir "0.3.5"]
                 [org.clojars.frozenlock/logger "2.0.35"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [clojure-complete "0.2.3"] ;; clojure-complete is necessary for embedded nREPL
                 [trptcolin/versioneer "0.1.1"]
                 [com.draines/postal "1.11.0"] ;; for email support
                 [liberator "0.11.0"]


                 ;;; cljs
                 [org.clojure/clojurescript "0.0-2156"]
                 [reagent "0.4.2"]
                 [org.clojars.frozenlock/query "0.1.1"]
                 [cljs-ajax "0.2.3"]]


  :uberjar-name "wacnet-webserver.jar"
  :min-lein-version "2.0.0"

  :plugins [[lein-cljsbuild "1.0.2"]
            [com.keminglabs/cljx "0.3.2"]]
  :hooks [;leiningen.cljsbuild
                                        ;cljx.hooks
          ]
  :cljx {:builds [{:source-paths ["src/cljx"]
                   :output-path "src-cross/clj"
                   :rules :clj}

                  {:source-paths ["src/cljx"]
                   :output-path "src-cross/cljs"
                   :rules :cljs}

                  ;;; cljs test don't seem to work for now...

                  {:source-paths ["test/cljx"]
                   :output-path "test-cross/clj"
                   :rules :clj}

                  {:source-paths ["test/cljx"]
                   :output-path "test-cross/cljs"
                   :rules :cljs}]}

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
                     :source-paths ["src/cljs" "src-cross/cljs" "src-cross/clj"]
                     :compiler {:output-to "resources/public/js/cljs.js"
                                :preamble ["reagent/react.js"]
                                :optimizations :whitespace
                                :pretty-print true}}}}


  :main wacnet.server)

