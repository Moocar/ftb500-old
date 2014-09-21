(ns me.moocar.ftb500.client.transport.inline.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport.inline :as client-inline-transport]
            [me.moocar.log :as log]))

(defn new-system [config]
  (component/system-map
   :client-transport (client-inline-transport/new-client-inline-transport)
   :log (log/new-channel-logger)))
