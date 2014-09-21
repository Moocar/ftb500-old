(ns me.moocar.ftb500.client.transport.inline.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport.inline :as client-inline-transport]))

(defn new-system [config]
  (component/system-map
   :client-transport (client-inline-transport/new-client-inline-transport)))
