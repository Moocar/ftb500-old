(ns me.moocar.ftb500.seats
  (:require [me.moocar.ftb500.schema :refer [seat? player? game? uuid?]])
  (:refer-clojure :exclude [next find]))

(defn seat=
  [seat1 seat2]
  (when (and seat1 seat2)
    (assert (seat? seat1))
    (assert (seat? seat2))
    (apply = (map :seat/id [seat1 seat2]))))

(defn find
  "Find the full seat for the partial seat ({:seat/id ...})"
  [partial-seat game]
  {:pre [(game? game)
         (uuid? (:seat/id partial-seat))]}
  (first (filter #(= (:seat/id %)
                     (:seat/id partial-seat))
                 (:game/seats game))))

(defn player=
  [player1 player2]
  {:pre [(player? player1)
         (player? player2)]}
  (apply = (map :user/id [player1 player2])))

(defn taken?
  "Returns true if the seat is already taken"
  [seat]
  {:pre [(seat? seat)]}
  (contains? seat :seat/player))

(defn taken-by?
  "Returns true if the seat is already taken by player"
  [seat player]
  {:pre [(seat? seat)
         (player? player)]}
  (and (taken? seat)
       (player= player (:seat/player seat))))

(defn next
  [seats seat]
  {:pre [(every? seat? seats)
         (not-empty seats)
         (seat? seat)]}
  (let [next-seat-position (mod (inc (:seat/position seat))
                                (count seats))]
    (first (filter #(= next-seat-position (:seat/position %))
                   seats))))
