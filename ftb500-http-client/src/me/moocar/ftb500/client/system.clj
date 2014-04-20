(ns me.moocar.ftb500.client.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]
            [me.moocar.log :as log]))

(defrecord DevSystem [clients log]
  component/Lifecycle
  (start [this]
    (let [log (component/start log)]
      (update-in this [:clients] #(map (fn [c] (component/start (assoc c :log log))) %))))
  (stop [this]
    this))

(defn new-system
  []
  (map->DevSystem {:log (log/new-logger nil)
                   :clients (repeatedly 4 client/new-http-client)}))
