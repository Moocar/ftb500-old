(ns me.moocar.ftb500.http.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as datomic]
            [me.moocar.log :as log]
            [me.moocar.ftb500.http.handler :as handler]
            [me.moocar.ftb500.handlers :as engine-handler]
            [me.moocar.ftb500.http.jetty :as jetty]
            [me.moocar.ftb500.http.websockets :as websockets]
            [me.moocar.ftb500.pubsub :as pubsub]))

(defn new-system
  []
  (let [config {:port 8081}]
    (component/system-map
     :datomic (datomic/new-datomic-database)
     :engine-handler (engine-handler/new-handler-component)
     :handler (handler/new-handler config)
     :pubsub (pubsub/new-pubsub config)
     :websockets (websockets/new-websockets config)
     :jetty-http (jetty/new-jetty-http config)
     :log (log/new-logger config))))
