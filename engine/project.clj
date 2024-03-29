(defproject me.moocar.ftb500/engine "0.1.0-SNAPSHOT"
  :description "Provides the server engine to handle a games state,
  including the database layer"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.datomic/datomic-free "0.9.5067" :exclusions [joda-time org.slf4j/slf4j-api org.slf4j/slf4j-nop]]
                 [com.stuartsierra/component "0.2.2"]
                 [me.moocar/lang "0.1.0-SNAPSHOT"]
                 [me.moocar.ftb500/pure-game "0.1.0-SNAPSHOT"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.5.9"]]}})
