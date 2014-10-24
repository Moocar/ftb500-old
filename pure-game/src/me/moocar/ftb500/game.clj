(ns me.moocar.ftb500.game
  (:require [me.moocar.ftb500.schema :refer [Game Seat Player Game]]
            [schema.core :as s]))

(defn full?
  [game]
  {:pre [(s/validate Game game)]}
  (= (count (:game/seats game))
     (count (filter :seat/player (:game/seats game)))))

(defn my-seat?
  [seat player]
  {:pre [(s/validate Seat seat)
         (s/validate Player player)]}
  (and (:seat/player seat)
       (= (:player/id player)
          (:seat/player seat))))

(defn seat-taken?
  [seat player]
  {:pre [(s/validate Seat seat)
         (s/validate Player player)]}
  (and (:seat/player seat)
       (not= (:player/id player)
             (:seat/player seat))))

(defn already-dealt?
  [game]
  {:pre [(s/validate Game game)]}
  (contains? game :game.kitty/cards))

(defn first-player?
  [game seat]
  {:pre [(s/validate Game game)
         (s/validate Seat seat)]}
  (= (:seat/id (:game/first-seat game))
     (:seat/id seat)))

(defn num-players
  [game]
  {:pre [(s/validate Game game)]}
  (:deck/num-players (:game/deck game)))
