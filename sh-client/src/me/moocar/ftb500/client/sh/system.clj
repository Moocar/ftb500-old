(ns me.moocar.ftb500.client.sh.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.sh :as sh-client]
            [me.moocar.ftb500.client.transport.websocket.system :as websocket-system]))

(defn sh-system [config]
  (component/system-map
   :sh-client (sh-client/new-sh-client config)))

(defn new-system [config]
  (merge (websocket-system/new-system config)
         (sh-system config)))


