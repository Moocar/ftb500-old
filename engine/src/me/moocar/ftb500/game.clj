(ns me.moocar.ftb500.game
  (:require [datomic.api :as d]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.request :as request]
            [me.moocar.ftb500.seats :as seats]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## API

(defn- make-game-tx
  [game-ext-id deck hands kitty]
  {:pre [game-ext-id (coll? deck) (coll? hands) (coll? kitty)]}
  (let [game-id (d/tempid :db.part/user)]
    (concat
     (map #(hash-map :db/id game-id
                     :game.kitty/cards (:db/id %))
          kitty)
     (mapcat #(seats/make-seat-tx game-id %1 %2)
             (range)
             hands)
     [{:db/id game-id
       :game/id game-ext-id
       :game/deck (:db/id deck)}])))

(defn add!
  [conn {:keys [player-id num-players]}]
  (request/wrap-bad-args-response
   [player-id (number? num-players)]
   (let [db (d/db conn)
         deck (deck/find db num-players)
         deck-cards (shuffle (:deck/cards deck))
         game-ext-id (d/squuid)
         {:keys [hands kitty]} (deck/partition-hands deck-cards)
         game-tx (make-game-tx game-ext-id deck hands kitty)
         result @(d/transact conn game-tx)]
     {:status 200
      :body {:game-id game-ext-id}})))
