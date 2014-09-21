(ns me.moocar.ftb500.engine.system
  (:require
   [com.stuartsierra.component :as component]
   [me.moocar.ftb500.engine.datomic :as datomic]
   [me.moocar.ftb500.engine.routes :as router]
   [me.moocar.ftb500.engine.routes.system :as route-system]
   [me.moocar.ftb500.engine.transport :as engine-transport]
   [me.moocar.ftb500.engine.transport.inline :as engine-inline-transport]
   [me.moocar.ftb500.engine.tx-handler.system :as tx-handler-system]
   [me.moocar.ftb500.engine.tx-listener :as tx-listener]
   [me.moocar.log :as log]))

(defn base-system
  [config]
  (component/system-map
   :datomic (datomic/new-datomic-database config)
   :engine-inline-transport (engine-inline-transport/new-engine-inline-transport)
   :engine-inline-sender (engine-inline-transport/new-engine-inline-sender)
   :engine-transport (engine-transport/new-engine-multi-transport [:engine-inline-sender])
   :user-store (engine-inline-transport/new-inline-user-store)
   :log (log/new-logger config)
   :server-listener (engine-transport/new-server-listener)
   :tx-listener (tx-listener/new-tx-listener)
   :router (router/new-router)))

(defn new-system
  [config]
  (merge
   (base-system config)
   (route-system/new-system config)
   (tx-handler-system/new-system config)))
