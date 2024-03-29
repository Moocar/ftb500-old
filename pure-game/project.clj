(defproject me.moocar.ftb500/pure-game "0.1.0-SNAPSHOT"
  :description "Provides the logic of the game through pure functions
  so they are available on server and client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.5.9"]
                                  [me.moocar.ftb500/generators "0.1.0-SNAPSHOT"]]}})
