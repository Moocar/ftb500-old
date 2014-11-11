(ns me.moocar.ftb500.card
  (:require [me.moocar.ftb500.schema :refer [card?]]))

(defn card=
  [card1 card2]
  {:pre [(card? card1) (card? card2)]}
  (and (= (:card.rank/name (:card/rank card1))
          (:card.rank/name (:card/rank card2)))
       (= (:card.suit/name (:card/suit card1))
          (:card.suit/name (:card/suit card2)))))
