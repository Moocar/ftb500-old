(ns me.moocar.ftb500.seats
  (:require [me.moocar.ftb500.schema :refer [seat? player? game? uuid?]])
  (:refer-clojure :exclude [next find]))

(defn find
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

(defn seat= 
  [seat1 seat2]
  {:pre [(seat? seat1)
         (seat? seat2)]}
  (apply = (map :seat/id [seat1 seat2])))

(defn next
  [seats seat]
  {:pre [(every? seat? seats)
         (not-empty seats)
         (seat? seat)]}
  (let [next-seat-position (mod (inc (:seat/position seat))
                                (count seats))]
    (->> seats
         (filter #(= next-seat-position (:seat/position %)))
         (first))))
