(defproject me.moocar/ftb500-web-service "0.1.0-SNAPSHOT"
  :description "Web Service for Flip the Bird 500"
  :url "http://ftb500.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.stuartsierra/component "0.2.1"]
                 [ring/ring-core "1.2.2"]
                 [info.sunng/ring-jetty9-adapter "0.6.0"]
                 [me.moocar/ftb500 "0.1.0-SNAPSHOT"]]

  :profiles {:dev {:source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]
                   :java-cmd "/Library/Java/JavaVirtualMachines/jdk1.7.0_51.jdk/Contents/Home/bin/java"}})
