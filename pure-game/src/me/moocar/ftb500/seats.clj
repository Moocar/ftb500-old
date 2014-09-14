(ns me.moocar.ftb500.seats
  (:refer-clojure :exclude [next]))

(defn next
  [seats seat]
  {:pre [seats seat]}
  (let [next-seat-position (mod (inc (:seat/position seat))
                                (count seats))]
    (->> seats
         (filter #(= next-seat-position (:seat/position %)))
         (first))))
