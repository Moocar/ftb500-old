(ns me.moocar.ftb500.engine.db.operations
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.engine.handler :refer [with-bad-args]]
            [me.moocar.ftb500.trick :as trick]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TX

(defn- action-tx
  [game-id action]
  (let [tx-id (d/tempid :db.part/tx)]
    [[:db/add tx-id :tx/game-id game-id]
     [:db/add tx-id :action action]]))

(defn- transact-action
  [this tx game-id action]
  (let [conn (:conn (:datomic this))]
    (d/transact conn (concat tx (action-tx game-id action)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Add Game

(defn- new-seat-tx [game-db-id position]
  (let [seat-db-id (d/tempid :db.part/user)
        seat-id (d/squuid)]
    [[:db/add seat-db-id :seat/id seat-id]
     [:db/add seat-db-id :seat/position position]
     [:db/add game-db-id :game/seats seat-id]]))

(defn- new-game-tx [game-id deck]
  {:pre [game-id (coll? deck)]}
  (let [game-db-id (d/tempid :db.part/user)]
    (concat
     (mapcat #(new-seat-tx game-db-id %) (range 4))
     [[:db/add game-db-id :game/id game-id]
      [:db/add game-db-id :game/deck (:db/id deck)]])))

(defn add!
  [this db client {:keys [num-players]}]
  (with-bad-args [(number? num-players)]
    (let [deck (card/find-deck db num-players)
          game-id (d/squuid)
          tx (new-game-tx game-id deck)]
      @(transact-action this tx game-id :action/create-game)
      [:success {:game/id game-id}])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Spectate

#_(defn spectate!
  [this db client {:keys [game player]}]
  (let [tx-listener (:tx-listener this)
        tx [[:db/add (:db/id game) :game/spectators (:db/id player)]]]
    #_(tx-listener/register-client tx-listener (:game/id game) client player)
    @(transact-action this tx (:game/id game) :action/spectate)
    [:success]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Join Game

(defn join!
  [this db client {:keys [game seat player]}]
  (with-bad-args [(game/full? game)
                  (game/seat-taken? seat player)]
    (let [tx [[:join-game (:db/id player) (:db/id game)]]]
      @(transact-action this tx (:game/id game) :action/join-game)
      [:success])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Deal cards

(defn- hand-cards-to-seat-tx [seat cards]
  (map #(vector [:db/add (:db/id seat):game.seat/cards (:db/id %)] cards)))

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

(defn deal!
  [this db client {:keys [game]}]
  (with-bad-args [(not (game/full? game))
                  (game/already-dealt? game)]
    (let [tx (new-deal-cards-tx game)]
      @(transact-action this tx (:game/id game) :action/deal-cards)
      [:success])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Bids

(defn get-bids [game]
  (sort-by :db/id (:game/bids game)))

(defn bid!
  [this db client {:keys [game seat bid]}]
  (let [game-bids (get-bids game)]
    (with-bad-args [(not (bid/passed-already? game-bids seat))
                    (bid/your-go? game game-bids seat)
                    (bid/positive-score? game-bids bid)
                    (not (bid/finished? game game-bids))]
      (let [game-bid-id (d/tempid :db.part/user)
            tx [[:db/add game-bid-id :bid (:db/id bid)]
                [:db/add game-bid-id :seat (:db/id seat)]
                [:db/add (:db/id game) :game/bids game-bid-id]]]
        @(transact-action this tx (:game/id game) :action/bid)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Kitty

(defn kitty-exchanged?
  [game]
  (let [db (d/entity-db game)
        num-players (count (:game/seats game))]
    (-> '[:find ?cards ?tx ?added
          :in $ ?game
          :where [?game :game.kitty/cards ?cards ?tx ?added]]
        (d/q (d/history db) (:db/id game))
        (count)
        (not= 3))))

(defn exchange-kitty!
  [this db client {:keys [game seat card-ks]}]
  (with-bad-args [(coll? card-ks)]
    (let [cards (map #(card/find db %) card-ks)
          game-bids (get-bids game)]
      (with-bad-args [(bid/finished? game game-bids)
                      (not (kitty-exchanged? game))
                      (= seat (:seat (bid/winning-bid)))
                      (= (count cards) 3)]
        (let [conn (:conn (:datomic this))
              current-kitty (:game.kitty/cards game)
              retract-tx (map #(vector :db/retract (:db/id game)
                                       :game.kitty/cards (:db/id %))
                              current-kitty)
              add-tx (map #(vector :db/add (:db/id game)
                                   :game.kitty/cards (:db/id %))
                          cards)]
          @(d/transact conn retract-tx)
          @(d/transact this add-tx (:game/id game) :action/exchange-kitty)
          [:success])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Play card

(defn get-tricks
  [game]
  (sort-by :db/id (:game/tricks game)))

(defn play-card!
  [this db client {:keys [game seat card]}]
  (let [tricks (get-tricks game)
        last-trick (last tricks)]
    (with-bad-args [(kitty-exchanged? game)
                    (trick/your-go?)]
      (let [new-trick? (or (empty? tricks)
                           (trick/trick-finished? game last-trick))
            trick-id (if new-trick?
                       (d/tempid :db.part/user)
                       (:db/id last-trick))
            trick-tx (when new-trick?
                       [[:db/add (:db/id game) :game/tricks trick-id]])
            play-id (d/tempid :db.part/user)
            play-tx [[:db/add trick-id :game.trick/plays play-id]
                     [:db/add play-id :trick.play/seat (:db/id seat)]
                     [:db/add play-id :trick.play/card (:db/id card)]
                     [:db/retract (:db/id seat) :game.seat/cards (:db/id card)]]
            tx (concat trick-tx play-tx)]
        @(transact-action this tx (:game/id game) :action/play-card)))))

(defn new-db-operations []
  (component/using {}
    [:datomic :log]))
