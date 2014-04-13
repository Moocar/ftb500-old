(ns me.moocar.ftb500.game
  (:require [datomic.api :as d]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.players :as players]
            [me.moocar.ftb500.request :as request]
            [me.moocar.ftb500.seats :as seats])
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
   [game player (keyword bid)]
   (if-let [seat (first (:game.seat/_player player))]
     (if-let [error (:error (bids/add! conn game seat bid))]
       {:status 400
        :body error}
       (let [bids (bids/get-bids (d/entity (d/db conn) (:db/id game)))]
         (if (bids/finished? bids (count (:game/seats game)))
           {:status 200
            :body {:kitty-cards (map card/ext-form (:game.kitty/cards game))}}
           {:status 200
            :body {}})))
     {:status 400
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))
