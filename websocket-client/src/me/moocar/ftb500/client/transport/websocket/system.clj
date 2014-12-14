(ns me.moocar.ftb500.client.transport.websocket.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport.websocket :as websocket-client]
            [me.moocar.log :as log]))

(defn new-system [config]
  (component/system-map
   :transport (websocket-client/new-websocket-client config)
   :log (log/new-channel-logger)))
