(ns me.moocar.ftb500.card
  (:require [me.moocar.ftb500.schema :as schema :refer [card?]]))

(defn- rank-name [card]
  (:card.rank/name (:card/rank card)))

(defn- suit-name [card]
  (:card.suit/name (:card/suit card)))

(defn card=
  [card1 card2]
  {:pre [(card? card1) (card? card2)]}
  (and (= (rank-name card1)
          (rank-name card2))
       (= (suit-name card1)
          (suit-name card2))))
