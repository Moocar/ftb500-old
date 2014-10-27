(ns me.moocar.ftb500.engine.routes.join-game
  (:require [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.routes.game-info :as game-info]
            [me.moocar.ftb500.engine.transport :as transport]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]
            [me.moocar.ftb500.engine.tx-listener :as tx-listener]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.seats :as seats]))

(defn uuid? [thing]
  (instance? java.util.UUID thing))

(defn- hand-cards-to-seat-tx [seat cards]
  (map #(vector :db/add (:db/id seat) :seat/cards (:db/id %))
       cards))

(defn- new-deal-cards-tx [game]
  (let [deck (:game/deck game)
        seats (:game/seats game)
        deck-cards (shuffle (:deck/cards deck))
        {:keys [hands kitty]} (card/partition-hands deck-cards)
        first-seat (:db/id (rand-nth (vec seats)))]
    (assert game)
    (assert (coll? seats))
    (assert (coll? hands))
    (assert (coll? kitty))
    (concat
     (mapcat #(vector [:db/add (:db/id game) :game.kitty/cards (:db/id %)]) kitty)
     (mapcat hand-cards-to-seat-tx seats hands)
     [[:db/add (:db/id game) :game/first-seat first-seat]])))

(defn- deal-cards [this game]
  (let [{:keys [datomic log]} this
        tx (new-deal-cards-tx game)]
    @(datomic/transact-action datomic tx (:game/id game) :action/deal-cards)))

(defrecord JoinGame [datomic log tx-listener deal-cards-delay]
  routes/Route
  (serve [this db request]
    (let [{:keys [logged-in-user-id body callback]} request
          {game-id :game/id seat-id :seat/id} body]
      (callback
       (cond (not logged-in-user-id) :must-be-logged-in
             (not game-id) :game-id-required
             (not seat-id) :seat-id-required
             (not (uuid? game-id)) :game-id-must-be-uuid
             (not (uuid? seat-id)) :seat-id-must-be-uuid

             :main
             (let [game (datomic/find db :game/id game-id)
                   seat (datomic/find db :seat/id seat-id)
                   player (datomic/find db :user/id logged-in-user-id)]
               (cond (not game) :game-does-not-exist
                     (not seat) :game-does-not-exist
                     (game/full? game) :game-is-already-full
                     (seats/taken-by? seat player) :seat-taken

                     :main
                     (let [tx [[:join-game (:db/id player) (:db/id game)]]]
                       (tx-listener/register-user-for-game tx-listener game-id logged-in-user-id)
                       (let [tx-result @(datomic/transact-action datomic tx game-id :action/join-game)
                             {:keys [db-after]} tx-result
                             game (d/entity db-after (:db/id game))]
                         (when (game/full? game)
                           (deal-cards this game))
                         [:success])))))))))

(defrecord JoinGameTxHandler [engine-transport]
  tx-handler/TxHandler
  (handle [this user-ids tx]
    (let [seat (datomic/get-attr tx :seat/player)
          player (:seat/player seat)
          msg {:route :join-game
               :body {:seat/id (:seat/id seat)
                      :seat/position (:seat/position seat)
                      :user/id (:user/id player)}}]
      (doseq [user-id user-ids]
        (transport/send! engine-transport user-id msg)))))

(defn get-seat
  [player]
  (first (:seat/_player player)))

(defrecord DealCardsTxHandler [engine-transport]
  tx-handler/TxHandler
  (handle [this user-ids tx]
    (doseq [user-id user-ids]
      (let [db (:db-after tx)
            game (datomic/get-attr tx :game/first-seat)
            player (datomic/find db :user/id user-id)
            seat (get-seat player)
            msg {:route :deal-cards
                 :body {:cards (map card/ext-form (:seat/cards seat))
                        :game/first-seat (select-keys (:game/first-seat game) [:seat/id])}}]
        (transport/send! engine-transport user-id msg)))))
