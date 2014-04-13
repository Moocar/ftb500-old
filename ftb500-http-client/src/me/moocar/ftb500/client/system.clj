(ns me.moocar.ftb500.client.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]))

(defrecord DevSystem [clients]
  component/Lifecycle
  (start [this]
    (update-in this [:clients] #(map component/start %)))
  (stop [this]
    this))

(defn new-system
  []
  (map->DevSystem {:clients (repeatedly 4 client/new-http-client)}))
