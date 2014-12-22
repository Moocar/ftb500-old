(ns me.moocar.ftb500.engine.system
  (:require
   [com.stuartsierra.component :as component]
   [me.moocar.ftb500.engine.datomic :as datomic]
   [me.moocar.ftb500.engine.routes :as router]
   [me.moocar.ftb500.engine.routes.system :as route-system]
   [me.moocar.ftb500.engine.transport.user-store :as user-store]
   [me.moocar.ftb500.engine.tx-handler.system :as tx-handler-system]
   [me.moocar.ftb500.engine.tx-listener :as tx-listener]
   [me.moocar.log :as log]))

(defn base-system
  [config]
  (component/system-map
   :datomic (datomic/new-datomic-database config)
   :engine-handler (router/new-engine-handler)
   :log (log/new-logger config)
   :user-store (user-store/default-user-store)
   :tx-listener (tx-listener/new-tx-listener)
   :router (router/new-router)))

(defn new-system
  [config]
  (merge
   (base-system config)
   (route-system/new-system config)
   (tx-handler-system/new-system config)))
