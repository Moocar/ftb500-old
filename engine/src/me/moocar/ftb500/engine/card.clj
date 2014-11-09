(ns me.moocar.ftb500.engine.card
  (:require [datomic.api :as d]
            [me.moocar.ftb500.schema :as schema :refer [ext-card?]])
  (:refer-clojure :exclude [find]))

(defn suit-ext-form
  [suit]
  suit)

(defn ext-form
  [card]
  (-> card
      (select-keys [:card/suit :card/rank])
      (update-in [:card/suit] select-keys [:card.suit/name])
      (update-in [:card/rank] select-keys [:card.rank/name])))

(defn partition-hands
  "Partitions a deck an into 4 hands of 10 cards each. Returns a map
  of :hands and :kitty"
  [deck]
  (let [partitions (partition-all 10 deck)]
    {:hands (take 4 partitions)
     :kitty (last partitions)}))

(defn find-deck
  "Loads the deck configuration for the number of players. E.g a 4
  player game does not contain 2s, 3s, or black 4s"
  [db num-players]
  (-> '[:find ?deck
        :in $ ?num-players
        :where [?deck :deck/num-players ?num-players]]
      (d/q db num-players)
      ffirst
      (->> (d/entity db))))

(defn find
  "Given an ext-card, returns that card's entity"
  [db {:keys [card/suit card/rank] :as card}]
  {:pre [(ext-card? card)]}
  (-> (if suit
        (-> '[:find ?card
              :in $ ?suit-name ?rank-name
              :where [?suit :card.suit/name ?suit-name]
              [?rank :card.rank/name ?rank-name]
              [?card :card/suit ?suit]
              [?card :card/rank ?rank]]
            (d/q db (:card.suit/name suit) (:card.rank/name rank)))
        (-> '[:find ?card
              :in $ ?rank-name
              :where [?rank :card.rank/name ?rank-name]
              [?card :card/rank ?rank]]
            (d/q db (:card.rank/name rank))))
      (ffirst)
      (->> (d/entity db))))
