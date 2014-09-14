;; This file was generated by me.moocar.system.dev.gen-project.
;; Do not edit by hand.
(defproject
 me.moocar/system.dev
 "0.1.0-SNAPSHOT"
 :aliases
 {"gen-project" ["run" "-m" "me.moocar.system.dev.gen-project"]}
 :profiles
 {:dev {:source-paths ["dev"]}}
 :dependencies
 [[org.clojure/java.classpath "0.2.2"]
  [clj-stacktrace "0.2.8"]
  [org.clojure/core.cache "0.6.3"]
  [http-kit "2.1.16"]
  [com.datomic/datomic-free "0.9.4880.2"]
  [com.stuartsierra/component "0.2.2"]
  [com.taoensso/sente "1.1.0"]
  [me.moocar/log "0.1.0-SNAPSHOT"]
  [me.moocar.ftb500/pure-game "0.1.0-SNAPSHOT"]
  [org.clojure/clojure "1.7.0-alpha1"]
  [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
  [ring/ring-core "1.3.1"]]
 :source-paths
 ["src"
  "../client/src"
  "../engine/src"
  "../http/src"
  "../log/src"
  "../pure-game/src"
  "../sente-client/src"]
 :resource-paths
 ["resources"
  "../client/resources"
  "../engine/resources"
  "../http/resources"
  "../pure-game/resources"
  "../sente-client/resources"]
 :test-paths
 ["test" "../engine/test" "../pure-game/test"]
 :jvm-opts
 ["-Xmx1g" "-XX:MaxPermSize=256m"])
