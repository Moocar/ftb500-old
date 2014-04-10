(ns me.moocar.ftb500.tricks
  (:require [datomic.api :as d]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.kitty :as kitty]))

(defn get-tricks
  [game]
  (sort-by :db/id (:game/tricks game)))

(defn finished?
  [trick]
  (let [game (first (:game/_tricks trick))
        seats (:game/seats game)]
    (= (count (:game.trick/plays trick))
       (count seats))))

(defn trump-left-suit
  [db trump-suit]
  (let [trump-color (:card.suit/color trump-suit)]
    (-> '[:find ?suit
          :in $ ?trump-color
          :where [?suit :card.suit/color ?trump-color]
          [(not= ?suit :card/suit trump-suit)]]
        (d/q db trump-suit)
        ffirst
        (->> (d/entity db)))))

(defn find-deck
  [db num-seats]
  (-> '[:find ?deck
        :in $ ?num-seats
        :where [?deck :deck/num-players ?num-seats]]
      (d/db db num-seats)
      ffirst
      (->> (d/entity db))))

(defn add!
  [this seat card]
  (let [game (first (:game/_seats seat))
        tricks (get-tricks game)
        last-trick (last tricks)]
    (if-not (kitty/exchanged? game)
      (throw (ex-info "Kitty not exchanged yet!"
                      {})))))
