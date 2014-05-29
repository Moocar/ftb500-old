(ns me.moocar.ftb500.client.engine.test
  (:require [clojure.test :refer :all]
            [clojure.core.async :refer [<!!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.engine.system :as system]
            [me.moocar.ftb500.client :as client]))

(defn uuid?
  [s]
  (instance? java.util.UUID s))

(deftest test-1
  (let [config {:datomic {:uri "datomic:mem://ftb500"}}
        base-system (component/start (system/transport-system config))
        client-system (component/start (merge base-system (system/new-client-system config)))
        client (:client client-system)]
    (is (uuid? (client/get-player-id client)))
    (let [response (<!! (client/create-game client))]
      (is (uuid? (:game-id response))))
    (component/stop client-system)
    (component/stop base-system)))

(deftest test-2
  (let [config {:datomic {:uri "datomic:mem://ftb500"}}
        base-system (component/start (system/transport-system config))
        client-system1 (component/start (merge base-system (system/new-client-system config)))
        client-system2 (component/start (merge base-system (system/new-client-system config)))
        client1 (:client client-system1)
        client2 (:client client-system2)]
    (<!! (client/create-game client1))
    (<!! (client/join-game client2 (client/get-game-id client1)))

    (component/stop client-system1)
    (component/stop client-system2)
    (component/stop base-system)))
