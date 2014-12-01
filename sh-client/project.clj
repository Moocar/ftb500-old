(defproject me.moocar.ftb500/sh-client "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"]
                 [com.stuartsierra/component "0.2.2"]
                 [me.moocar.ftb500/client "0.1.0-SNAPSHOT"]
                 [org.eclipse.jetty.websocket/websocket-client "9.2.3.v20140905"]]
  :profiles {:dev {:dependencies [[ch.qos.logback/logback-classic "1.1.2"]
                                  [org.slf4j/log4j-over-slf4j "1.7.7" :exclusions [org.slf4j/slf4j-api]]
                                  [org.slf4j/jul-to-slf4j "1.7.7" :exclusions [org.slf4j/slf4j-api]]
                                  [org.slf4j/jcl-over-slf4j "1.7.7" :exclusions [org.slf4j/slf4j-api]]
                                  ]}}
  :main me.moocar.ftb500.sh-client)
