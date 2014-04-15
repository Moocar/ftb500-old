(ns me.moocar.ftb500.game
  (:require [datomic.api :as d]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.game-view :as game-view]
            [me.moocar.ftb500.kitty :as kitty]
            [me.moocar.ftb500.players :as players]
            [me.moocar.ftb500.request :as request]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.tricks :as tricks])
  (:refer-clojure :exclude [find]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## API

(defn find
  [db ext-id]
  (db/find db :game/id ext-id))

(defn- make-game-tx
  [game-ext-id deck hands kitty player]
  {:pre [game-ext-id (coll? deck) (coll? hands) (coll? kitty)]}
  (let [game-id (d/tempid :db.part/user)]
    (concat
     (map #(hash-map :db/id game-id
                                 :game.kitty/cards (:db/id %))
                      kitty)
     (seats/make-seat-tx game-id 0 (first hands) player)
     (mapcat #(seats/make-seat-tx game-id %1 %2)
             (range 1 4)
             (rest hands))
     [{:db/id game-id
       :game/id game-ext-id
       :game/deck (:db/id deck)}])))

(defn add!
  [conn {:keys [player num-players]}]
  (request/wrap-bad-args-response
   [player (number? num-players)]
   (let [db (d/db conn)
         deck (deck/find db num-players)
         deck-cards (shuffle (:deck/cards deck))
         game-ext-id (d/squuid)
         {:keys [hands kitty]} (deck/partition-hands deck-cards)
         game-tx (make-game-tx game-ext-id deck hands kitty player)
         result @(d/transact conn game-tx)]
     {:status 200
      :body {:game-id game-ext-id
             :cards (map card/ext-form (first hands))}})))

(defn join!
  [conn {:keys [game player]}]
  (request/wrap-bad-args-response
   [player game]
   (if-let [seat (seats/next-vacant game)]
     (let [cards (:game.seat/cards seat)]
       @(d/transact conn
                    [{:db/id (:db/id seat)
                      :game.seat/player (:db/id player)}])
       {:status 200
        :body {:cards (map card/ext-form cards)}})
     {:status 400
      :body {:msg "No more seats left at this game"}})))

(defn bid!
  [conn {:keys [game player bid]}]
  (request/wrap-bad-args-response
   [game player (keyword? bid)]
   (if-let [seat (first (:game.seat/_player player))]
     (if-let [error (:error (bids/add! conn game seat bid))]
       {:status 400
        :body error}
       {:status 200
        :body {}})
     {:status 400
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))

(defn exchange-kitty!
  [conn {:keys [game player cards]}]
  (request/wrap-bad-args-response
   [game player (coll? cards)]
   (if-let [seat (first (:game.seat/_player player))]
     (let [db (d/db conn)
           card-entities (map #(card/find db %) cards)]
       (if-let [error (:error (kitty/exchange! conn seat card-entities))]
         {:status 400
          :body error}
         {:status 200
          :body {}}))
     {:status 400
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))

(defn play-card!
  [conn {:keys [game player card]}]
  (request/wrap-bad-args-response
   [game player (map? card)]
   (if-let [seat (first (:game.seat/_player player))]
     (let [db (d/db conn)
           card-entity (card/find db card)]
       (if-let [error (:error (tricks/add-play! conn seat card-entity))]
         {:status 400
          :body error}
         {:status 200
          :body {}}))
     {:status 400
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))

(defn view
  [conn {:keys [game player]}]
  (request/wrap-bad-args-response
   [game player]
   (if-let [seat (first (:game.seat/_player player))]
     (let [db (d/db conn)]
       {:status 200
        :body (game-view/view db game player)})
     {:status 400
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))
