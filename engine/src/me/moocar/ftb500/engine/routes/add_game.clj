(ns me.moocar.ftb500.engine.routes.add-game
  (:require [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.async :as moo-async]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]))

(defn- new-seat-tx [game-db-id position]
  (let [seat-db-id (d/tempid :db.part/user)
        seat-id (d/squuid)]
    [[:db/add seat-db-id :seat/id seat-id]
     [:db/add seat-db-id :seat/position position]
     [:db/add game-db-id :game/seats seat-db-id]]))

(defn- new-game-tx [game-id deck]
  {:pre [game-id (coll? deck)]}
  (let [game-db-id (d/tempid :db.part/user)]
    (concat
     (mapcat #(new-seat-tx game-db-id %) (range 4))
     [[:db/add game-db-id :game/id game-id]
      [:db/add game-db-id :game/deck (:db/id deck)]])))

(defrecord AddGame [datomic log]
  routes/Route
  (serve [this db request]
    (let [{:keys [body logged-in-user-id]} request
          {:keys [num-players]} body]
      (cond
        (not num-players) [:num-players-required]
        (not (number? num-players)) [:num-players-must-be-number]
        (not logged-in-user-id) [:must-be-logged-in]
        
        :else
        (let [deck (card/find-deck db num-players)
              game-id (d/squuid)
              tx (new-game-tx game-id deck)]
          @(datomic/transact-action datomic tx game-id :action/create-game)
          [:success {:game/id game-id}])))))

(defrecord AddGameTxHandler [user-store]
  tx-handler/TxHandler
  (handle [this user-ids tx]
    (doseq [user-id user-ids]
      (doseq [conn (user-store/user-conns user-store user-id)]
        (moo-async/send-off! (:send-ch conn) {:route :create-game})))))
