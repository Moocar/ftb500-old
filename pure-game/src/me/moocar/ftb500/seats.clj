(ns me.moocar.ftb500.seats
  (:refer-clojure :exclude [next]))

(defn seat= [seat1 seat2]
  (= (:seat/id seat1)
     (:seat/id seat2)))

(defn next
  [seats seat]
  {:pre [seats seat (not-empty seats) (every? :seat/position seats) (:seat/position seat)]}
  (let [next-seat-position (mod (inc (:seat/position seat))
                                (count seats))]
    (->> seats
         (filter #(= next-seat-position (:seat/position %)))
         (first))))
