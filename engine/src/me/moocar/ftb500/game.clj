(ns me.moocar.ftb500.game
  (:require [datomic.api :as d]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.players :as players]
            [me.moocar.ftb500.request :as request]
            [me.moocar.ftb500.seats :as seats])
  (:refer-clojure :exclude [find]))

(defn uuid? [s]
  (instance? java.util.UUID s))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## API

(defn find
  [db ext-id]
  (when-let [id (-> '[:find ?entity
                      :in $ ?ext-id
                      :where [?entity :game/id ?ext-id]]
                    (d/q db ext-id)
                    ffirst)]
    (d/entity db id)))

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
  [conn {:keys [player-id num-players]}]
  (request/wrap-bad-args-response
   [(uuid? player-id) (number? num-players)]
   (let [db (d/db conn)
         player (players/find db player-id)]
     (request/wrap-bad-args-response
      [player]
      (let [deck (deck/find db num-players)
            deck-cards (shuffle (:deck/cards deck))
            game-ext-id (d/squuid)
            {:keys [hands kitty]} (deck/partition-hands deck-cards)
            game-tx (make-game-tx game-ext-id deck hands kitty player)
            result @(d/transact conn game-tx)]
        {:status 200
         :body {:game-id game-ext-id
                :cards (map card/ext-form (first hands))}})))))

(defn join!
  [conn {:keys [game-id player-id]}]
  (request/wrap-bad-args-response
   [(uuid? game-id) (uuid? player-id)]
   (let [db (d/db conn)
         player (players/find db player-id)
         game (find db game-id)]
     (request/wrap-bad-args-response
      [player game]
      (let [seat (seats/next-vacant game)]
        (if-not seat
          {:status 400
           :body {:msg "No more seats left at this game"}}
          (let [cards (:game.seat/cards seat)]
            @(d/transact conn
                         [{:db/id (:db/id seat)
                           :game.seat/player (:db/id player)}])
            {:status 200
             :body {:cards (map card/ext-form cards)}})))))))


(defn bid!
  [conn {:keys [game-id player-id bid]}]
  (request/wrap-bad-args-response
   [(uuid? game-id) (uuid? player-id) (keyword bid)]
   (let [db (d/db conn)
         player (players/find db player-id)
         seat (first (:game.seat/_player player))
         game (find db game-id)]
     (request/wrap-bad-args-response
      [player game seat]
      (bids/add! conn game seat bid)
      {:status 200
       :body {}}))))
