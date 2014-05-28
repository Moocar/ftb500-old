(ns me.moocar.ftb500.client.engine.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.handlers :as handler]
            [me.moocar.ftb500.client.engine.requester :as requester]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.clients :as clients]
            [me.moocar.ftb500.client.transport :as transport]
            [me.moocar.ftb500.pubsub2 :as pubsub]
            [me.moocar.log :as log]))

(defn transport-system
  [config]
  (component/system-map
   :log (log/new-logger config)
   :requester (requester/new-requester)
   :clients (clients/new-clients)
   :clients-handler (handler/new-handler-component)
   :pubsub (pubsub/new-pubsub config)
   :datomic (db/new-datomic-database config)
   :handler (handler/new-handler-component)))

(defn new-client-system
  [config]
  (component/system-map
   :transport (transport/new-client-transport)
   :transport-handler (client/new-transport-handler)
   :client (client/new-client config)))

(defrecord AutoplaySystem [config clients]
  component/Lifecycle
  (start [this]
    (let [system (component/start-system (transport-system config))
          clients (doall (map #(component/start-system (merge system %)) clients))]
      (assoc system :clients clients)))
  (stop [this]
    (let [clients (map component/stop-system clients)]
      (-> this
          component/stop-system
          (assoc :clients clients)))))

(defn new-autoplay-system
  []
  (let [config {:datomic {:uri "datomic:free://localhost:4334/ftb500"}}
        clients (repeatedly 4 #(new-client-system config))]
    (map->AutoplaySystem {:config config
                          :clients clients})))
