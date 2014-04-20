(ns me.moocar.ftb500.pubsub
  (:require [clojure.core.async :refer [put!]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.seats :as seats]))

(defn register-client
  [this client-map]
  (log/log (:log this) {:msg "register-client"
                        :client-map client-map})
  (let [output-ch (:output-ch client-map)]
    (put! output-ch {:action :registered})
    (swap! (:client-db this)
          conj client-map)))

(defn join-game-listener
  [component tx-data]
  (let [log (:log component)
        datomic (:datomic component)
        conn (:conn datomic)
        attr-id (:id (d/attribute (d/db conn) :game.seat/player))]
    (when-let [seat (-> '[:find ?eid
                          :in $ ?seat-attr-id
                          :where [?eid ?seat-attr-id]]
                        (d/q tx-data attr-id)
                        ffirst
                        (->> (d/entity (d/db conn))))]
      (let [game (first (:game/_seats seat))
            game-id (:game/id game)]
        (let [game-clients (filter (fn [client-map]
                                     (= (:game-id client-map)
                                        game-id))
                                   @(:client-db component))]
          (doseq [game-client game-clients]
            (put! (:output-ch game-client)
                  {:action :player-joined
                   :player (seats/ext-form seat)})))))))

(defn start-db-listener
  [component]
  (let [datomic (:datomic component)
        tx-report-queue (:tx-report-queue datomic)
        conn (:conn datomic)
        attr-id (:id (d/attribute (d/db conn) :game.bid/bid))]
    (future
      (log/log (:log component) {:msg "Starting pubsub loop"})
      (loop [tx (.take tx-report-queue)]
        (try
          (join-game-listener component (:tx-data tx))
          (catch Throwable e
            (log/log (:log component)
                     {:msg "error in pubsub loop"
                      :ex e})))
        (recur (.take tx-report-queue))))))

(defrecord Pubsub [datomic client-db log]
  component/Lifecycle
  (start [this]
    (start-db-listener this)
    this)
  (stop [this]
    (d/remove-tx-report-queue (:conn datomic))
    this))

(defn new-pubsub
  [config]
  (component/using (map->Pubsub {:client-db (atom [])})
    [:datomic :log]))
