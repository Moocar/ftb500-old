(ns me.moocar.ftb500.seats
  (:refer-clojure :exclude [next]))

(defn next
  [game-seats seat]
  {:pre [game-seats seat]}
  (let [next-seat-position (mod (inc (:game.seat/position seat))
                                (count game-seats))]
    (->> game-seats
         (filter #(= next-seat-position (:game.seat/position %)))
         (first))))
