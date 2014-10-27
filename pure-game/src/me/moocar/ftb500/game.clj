(ns me.moocar.ftb500.game
  (:require [me.moocar.ftb500.schema :refer [game? seat?]]
            [me.moocar.ftb500.seats :as seats]))

(defn full?
  [game]
  {:pre [(game? game)]}
  (= (count (:game/seats game))
     (count (filter :seat/player (:game/seats game)))))

(defn already-dealt?
  [game]
  {:pre [(game? game)]}
  (contains? game :game.kitty/cards))

(defn first-player?
  [game seat]
  {:pre [(game? game)
         (seat? seat)]}
  (seats/seat= (:game/first-seat game) seat))

(defn num-players
  [game]
  {:pre [(game? game)]}
  (:deck/num-players (:game/deck game)))
