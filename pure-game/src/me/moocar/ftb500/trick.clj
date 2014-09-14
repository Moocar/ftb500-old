(ns me.moocar.ftb500.trick
  (:require [me.moocar.ftb500.bid :as bid]))

(defn your-go? [game seats tricks last-trick current-plays seat game-bids plays]
  (or (and (empty? tricks)
           (= seat (:seat (bid/winning-bid game-bids))))))

(defn trick-finished?
  [game trick]
  (= (count (:game/seats game))
     (count (:game.trick/plays trick))))
