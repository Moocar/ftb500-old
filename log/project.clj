(defproject me.moocar/log "0.1.0-SNAPSHOT"
  :description "Provides a component based log api backed by a core
  async channel"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha1"]
                 [org.clojure/core.async "0.1.338.0-5c5012-alpha"]
                 [com.stuartsierra/component "0.2.2"]])
