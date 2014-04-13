(ns me.moocar.ftb500.seats
  (:require [datomic.api :as d])
  (:refer-clojure :exclude [next]))

(defn make-seat-tx
  [game-id index cards]
  (let [seat-id (d/tempid :db.part/user)]
    (concat
     (map #(hash-map :db/id seat-id
                     :game.seat/cards (:db/id %))
          cards)
     [{:db/id seat-id
       :game.seat/position index}
      {:db/id game-id
       :game/seats seat-id}])))

(defn next
  [game-seats seat]
  {:pre [game-seats seat]}
  (let [next-seat-position (mod (inc (:game.seat/position seat))
                                (count game-seats))]
    (->> game-seats
         (filter #(= next-seat-position (:game.seat/position %)))
         (first))))
