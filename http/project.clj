(defproject me.moocar.ftb500/http "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha4"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [com.stuartsierra/component "0.2.2"]
                 [info.sunng/ring-jetty9-adapter "0.7.2"]
                 [me.moocar.ftb500/engine "0.1.0-SNAPSHOT" :exclusions [org.slf4j/slf4j-api]]
                 [ring/ring-core "1.3.1" :exclusions [commons-codec]]])
