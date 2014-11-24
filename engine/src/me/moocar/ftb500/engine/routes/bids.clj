(ns me.moocar.ftb500.engine.routes.bids
  (:require [datomic.api :as d]
            [me.moocar.lang :refer [uuid?]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.datomic.schema :as db-schema]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport :as transport]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]
            [me.moocar.ftb500.schema :refer [bid-names]]
            [me.moocar.ftb500.seats :refer [seat=]]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn find-bid [db bid-name]
  (-> '[:find ?bid
        :in $ ?bid-name
        :where [?bid :bid/name ?bid-name]]
      (d/q db bid-name)
      ffirst
      (->> (d/entity db))))

(defn implementation [this db request]
  (let [{:keys [datomic log]} this
        {:keys [body logged-in-user-id callback]} request
        {bid-name :bid/name seat-id :seat/id} body]
    (callback
     (cond

      ;; Check basic inputs

      (not logged-in-user-id) :must-be-logged-in
      (and bid-name (not ((set bid-names) bid-name))) :unknown-bid
      (not seat-id) :seat-id-required
      (not (uuid? seat-id)) :seat-id-must-be-uuid

      :else ;; Load entities

      (let [bid (when bid-name (find-bid db bid-name))
            seat (datomic/find db :seat/id seat-id)
            pre-game (first (:game/_seats seat))
            game (db-schema/touch-game pre-game)]

        (cond

         ;; Make sure entities exist

         (and bid-name (not bid)) :invalid-bid
         (not seat) :seat-not-found

         ;; Game validations

         (bid/passed? game seat) :you-have-already-passed
         (not (seat= (bid/next-seat game) seat)) :its-not-your-go
         (and bid-name (not (bid/valid? game bid))) :score-not-high-enough

         :else ;; Perform actual transaction

         (let [game-bid-id (d/tempid :db.part/user)
               tx (concat
                   (when bid-name
                     [[:db/add game-bid-id :player-bid/bid (:db/id bid)]])
                   [[:db/add game-bid-id :player-bid/seat (:db/id seat)]
                    [:db/add (:db/id game) :game/bids game-bid-id]])]
           @(datomic/transact-action datomic tx (:game/id game) :action/bid)
           [:success])))))))

(defrecord Bid [datomic log]
  routes/Route
  (serve [this db request]
    (implementation this db request)))

(defn seat-user-id
  [db seat]
  (:user/id (:seat/player seat)))

(defn- hand-cards-to-seat-tx [seat cards]
  (map #(vector :db/add (:db/id seat)
                :seat/cards (:db/id %))
       cards))

(defn- add-kitty-to-hand-tx
  [game seat]
  (let [kitty-cards (:game.kitty/cards game)
        retract-kitty-tx (map #(vector :db/retract (:db/id game)
                                       :game.kitty/cards (:db/id %))
                              kitty-cards)
        add-to-hand-tx (hand-cards-to-seat-tx seat kitty-cards)]
    (concat retract-kitty-tx add-to-hand-tx)))

(defn handle-last-bid [datomic tx game connected-user-ids]
  (let [winning-bid (bid/winner game)
        _ (assert winning-bid)
        winning-seat (:player-bid/seat winning-bid)
        _ (assert winning-seat)
        winning-seat-user-id (:user/id (:seat/player winning-seat))]
    (assert winning-seat-user-id)
    (when (contains? (set connected-user-ids) winning-seat-user-id)
      (let [conn (:conn datomic)
            new-tx (add-kitty-to-hand-tx game winning-seat)]
        @(d/transact conn new-tx)
        (let [msg {:route :kitty
                   :body (datomic/pull (:db-after tx)
                                       [{:game.kitty/cards db-schema/card-ext-pattern}]
                                       (:db/id game))}]
          [[winning-seat-user-id msg]])))))

(defrecord BidTxHandler [datomic engine-transport log]
  tx-handler/TxHandler
  (handle [this user-ids tx]
    (let [db (:db-after tx)
          bid (datomic/get-attr tx :player-bid/seat)
          game (db-schema/touch-game (datomic/get-attr tx :game/bids))
          msg {:route :bid
               :body {:bid (datomic/pull db db-schema/player-bid-ext-pattern (:db/id bid))}}
          user-msgs (-> user-ids
                        (->> (map #(vector % msg)))
                        (cond-> (bid/finished? game)
                                (concat (handle-last-bid datomic tx game user-ids))))]
      (doseq [[user-id msg] user-msgs]
        (transport/send! engine-transport user-id msg)))))
