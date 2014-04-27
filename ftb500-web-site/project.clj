(defproject me.moocar/ftb500-web-site "0.1.0-SNAPSHOT"
  :description "Web site for Flip the Bird 500 (FTB500)"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2202"]
                 [com.stuartsierra/component "0.2.1"]
                 [ring/ring-core "1.2.2"]
                 [ring/ring-jetty-adapter "1.3.0-beta1"]
                 [me.moocar/log "0.1.0-SNAPSHOT"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :java-cmd "/Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home/bin/java"
                   :plugins [[lein-cljsbuild "1.0.3"]]}}
  :cljsbuild {:builds [{;; The path to the top-level ClojureScript source directory:
                        :source-paths ["src-cljs"]
                        :compiler {;; The standard ClojureScript compiler options:
                                   ;; (See the ClojureScript compiler documentation for details.)
                                   :output-to "war/javascripts/main.js"  ; default: target/cljsbuild-main.js
                                   :optimizations :whitespace
                                   :pretty-print true}}]})
