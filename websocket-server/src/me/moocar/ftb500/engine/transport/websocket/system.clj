(ns me.moocar.ftb500.engine.transport.websocket.system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.engine.transport.websocket]
            [me.moocar.log :as log]))

(defn- websocket-system [config]
  (component/system-map
   :transport (me.moocar.ftb500.engine.transport.websocket/new-websocket-server config)
   :log-ch (async/chan 1 (keep (comp println log/format-log)))))

(defn new-system [config]
  (merge
   (engine-system/new-system config)
   (websocket-system config)))
