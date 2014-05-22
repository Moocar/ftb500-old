(ns me.moocar.ftb500.client.engine.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.handlers :as handler]
            [me.moocar.ftb500.client.engine.requester :as requester]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.pubsub2 :as pubsub]
            [me.moocar.log :as log]))

(defn new-system
  []
  (let [config {:datomic {:uri "datomic:free://localhost:4334/ftb500"}}]
   (component/system-map
    :datomic (db/new-datomic-database config)
    :handler (handler/new-handler-component)
    :pubsub (pubsub/new-pubsub config)
    :log (log/new-logger config)
    :requester (requester/new-requester)
    :client1 (client/new-client config)
    :client2 (client/new-client (assoc config :player-name "Bart"))
    :client3 (client/new-client config)
    :client4 (client/new-client config))))
