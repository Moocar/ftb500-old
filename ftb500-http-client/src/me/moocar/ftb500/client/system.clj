(ns me.moocar.ftb500.client.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]))

(defn new-system
  []
  (component/system-map
   :client (client/new-http-client)))
