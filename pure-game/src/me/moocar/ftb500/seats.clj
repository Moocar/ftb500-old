(ns me.moocar.ftb500.seats
  (:require [me.moocar.ftb500.schema :refer [Seat]] 
            [schema.core :as s])
  (:refer-clojure :exclude [next]))

(defn seat= 
  [seat1 seat2]
  {:pre [(s/validate Seat seat1)
         (s/validate Seat seat2)]}
  (= (:seat/id seat1)
     (:seat/id seat2)))

(defn next
  [seats seat]
  {:pre [(s/validate (s/both [Seat] (s/pred seq 'seq)) seats)
         (s/validate Seat seat)]}
  (let [next-seat-position (mod (inc (:seat/position seat))
                                (count seats))]
    (->> seats
         (filter #(= next-seat-position (:seat/position %)))
         (first))))
