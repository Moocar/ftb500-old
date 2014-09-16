(ns me.moocar.ftb500.engine.system
  (:require
   [com.stuartsierra.component :as component]
   [me.moocar.ftb500.engine.datomic :as datomic]
   [me.moocar.ftb500.engine.routes :as router]
   [me.moocar.ftb500.engine.transport :as engine-transport]
   [me.moocar.ftb500.engine.transport.inline :as engine-inline-transport]
   [me.moocar.ftb500.engine.routes.system :as route-system]
   [me.moocar.log :as log]))

(defn base-system
  [config]
  (component/system-map
   :datomic (datomic/new-datomic-database config)
   :engine-inline-transport (engine-inline-transport/new-engine-inline-transport)
   :engine-transport (engine-transport/new-engine-multi-transport [:engine-inline-transport])
   :log (log/new-logger config)
   :server-listener (engine-transport/new-server-listener)
   :router (router/new-router)))

(defn new-system
  [config]
  (merge
   (base-system config)
   (route-system/new-system config)))
