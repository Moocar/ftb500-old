(ns me.moocar.ftb500.http.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as datomic]
            [me.moocar.ftb500.http.handler :as handler]
            [me.moocar.ftb500.handlers :as engine-handler]
            [me.moocar.ftb500.http.jetty :as jetty]))

(defn new-system
  []
  (let [config {:port 8080}]
    (component/system-map
     :datomic (datomic/new-datomic-database)
     :engine-handler (engine-handler/new-handler-component)
     :handler (handler/new-handler config)
     :jetty-http (jetty/new-jetty-http config))))
