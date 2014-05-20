(defproject me.moocar/ftb500-engine-client "0.1.0-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [me.moocar/ftb500-protocols "0.1.0-SNAPSHOT"]
                 [me.moocar/ftb500-client "0.1.0-SNAPSHOT"]
                 [me.moocar/ftb500 "0.1.0-SNAPSHOT"]
                 [me.moocar/log "0.1.0-SNAPSHOT"]]
  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :jvm-opts ["-Xmx1g"]}})
