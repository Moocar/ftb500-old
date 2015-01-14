(ns me.moocar.ftb500.client.sh.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.sh :as sh-client]
            [me.moocar.ftb500.client.transport.websocket.system :as websocket-system]
            [me.moocar.log :as log]))

(defn sh-system [console config]
  (component/system-map
   :sh-client (sh-client/new-sh-client console config)
   :log (log/new-logger config)))

(defn new-system [console config]
  (merge (websocket-system/new-system config)
         (sh-system console config)))
