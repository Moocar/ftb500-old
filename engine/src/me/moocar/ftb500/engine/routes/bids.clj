(ns me.moocar.ftb500.engine.routes.bids
  (:require [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport :as transport]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]
            [me.moocar.ftb500.schema :refer [bid-names]]
            [me.moocar.ftb500.seats :refer [seat=]]))

(defn uuid? [thing]
  (instance? java.util.UUID thing))

(defn log [this msg]
  (log/log (:log this) msg))

(defn ext-form
  [bid]
  (-> bid
      (select-keys [:bid/name
                    :bid/tricks
                    :bid/suit
                    :bid/contract-style
                    :bid/score])
      (cond-> (:bid/suit bid)
              (update-in [:bid/suit] card/suit-ext-form))))

(defrecord BidTable [datomic log]
  routes/Route
  (serve [this db request]
    (let [{:keys [callback]} request]
      (callback
       (->> (datomic/find db :bid/name)
            (sort-by :bid/score)
            (map ext-form))))))

(defn find-bid [db bid-name]
  (-> '[:find ?bid
        :in $ ?bid-name
        :where [?bid :bid/name ?bid-name]]
      (d/q db bid-name)
      ffirst
      (->> (d/entity db))))

(defn touch-game
  [game]
  (-> game
      (->> (into {}))
      (update-in [:game/bids] #(reverse (sort-by :db/id %)))
      (update-in [:game/seats] #(sort-by :seat/position %))
      (assoc :db/id (:db/id game))))

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
            game (touch-game pre-game)
            {:keys [game/seats game/bids]} game]

        (cond

         ;; Make sure entities exist

         (and bid-name (not bid)) :invalid-bid
         (not seat) :seat-not-found

         ;; Game validations

         (bid/passed? bids seat) :you-have-already-passed
         (not (seat= (bid/next-seat game) seat)) :its-not-your-go
         (and bid-name
              (<= (:bid/score bid)
                  (bid/highest-score bids))) :score-not-high-enough

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

(defn add-kitty-to-hand-tx
  [game seat]
  (let [kitty-cards (:game.kitty/cards game)
        retract-kitty-tx (map #(vector :db/retract (:db/id game)
                                       :game.kitty/cards (:db/id %))
                              kitty-cards)
        add-to-hand-tx (hand-cards-to-seat-tx seat kitty-cards)]
    (concat retract-kitty-tx add-to-hand-tx)))

(defn handle-last-bid [datomic tx game connected-user-ids]
  (let [{:keys [game/bids]} game
        _ (assert bids)
        winning-bid (bid/winning-bid bids)
        _ (assert winning-bid)
        winning-seat (:player-bid/seat winning-bid)
        _ (assert winning-seat)
        winning-seat-user-id (:user/id (:seat/player winning-seat))]
    (assert winning-seat-user-id)
    (when (contains? (set connected-user-ids) winning-seat-user-id)
      (let [conn (:conn datomic)
            tx (add-kitty-to-hand-tx game winning-seat)]
        @(d/transact conn tx)
        (let [kitty-cards (:game.kitty/cards game)
              msg {:route :kitty
                   :body {:cards (map card/ext-form kitty-cards)}}]
          [[winning-seat-user-id msg]])))))

(defrecord BidTxHandler [datomic engine-transport log]
  tx-handler/TxHandler
  (handle [this user-ids tx]
    (let [bid (datomic/get-attr tx :player-bid/seat)
          game (touch-game (datomic/get-attr tx :game/bids))
          {:keys [player-bid/bid player-bid/seat]} bid
          msg {:route :bid
               :body  {:bid (cond-> {:player-bid/seat {:seat/id (:seat/id seat)}}
                                    bid (assoc :player-bid/bid (ext-form bid)))}}
          user-msgs (-> user-ids
                        (->> (map #(vector % msg)))
                        (cond-> (bid/finished? game)
                                (concat (handle-last-bid datomic tx game user-ids))))]
      (doseq [[user-id msg] user-msgs]
        (transport/send! engine-transport user-id msg)))))
