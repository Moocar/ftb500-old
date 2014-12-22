(ns me.moocar.ftb500.engine.transport.websocket.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.engine.transport.websocket]))

(defn- websocket-system [config]
  (component/system-map
   :transport (me.moocar.ftb500.engine.transport.websocket/new-websocket-server config)))

(defn new-system [config]
  (merge
   (engine-system/new-system config)
   (websocket-system config)))

