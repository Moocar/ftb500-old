(ns me.moocar.ftb500.client.transport.websocket.system
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport.websocket :as websocket-client]
            [me.moocar.log :as log]))

(defn new-system [config]
  (component/system-map
   :engine-transport (websocket-client/new-websocket-client config)
   :log-ch (async/chan 1 (keep (comp println log/format-log)))))
