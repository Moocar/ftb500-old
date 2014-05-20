(defproject me.moocar/ftb500 "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/core.async "0.1.278.0-76b25b-alpha"]
                 [com.datomic/datomic-free "0.9.4699"]
                 [com.stuartsierra/component "0.2.1"]
                 [me.moocar/log "0.1.0-SNAPSHOT"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]}})
