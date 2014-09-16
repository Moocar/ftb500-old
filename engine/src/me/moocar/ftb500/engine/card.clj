(ns me.moocar.ftb500.engine.card
  (:require [datomic.api :as d])
  (:refer-clojure :exclude [find]))

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
  "Given a card in the format {:suit :hearts :rank :five}, returns
  that card's entity. Note a joker does not have a suit and is
  represented as {:rank :joker}"
  [db {:keys [suit rank]}]
  (if suit
    (let [full-suit (keyword "card.suit.name" (name suit))
          full-rank (keyword "card.rank.name" (name rank))]
      (-> '[:find ?card
            :in $ ?suit-name ?rank-name
            :where [?suit :card.suit/name ?suit-name]
            [?rank :card.rank/name ?rank-name]
            [?card :card/suit ?suit]
            [?card :card/rank ?rank]]
          (d/q db full-suit full-rank)
          (ffirst)
          (->> (d/entity db))))
    (let [full-rank (keyword "card.rank.name" (name rank))]
      (-> '[:find ?card
            :in $ ?rank-name
            :where [?rank :card.rank/name ?rank-name]
            [?card :card/rank ?rank]]
          (d/q db full-rank)
          (ffirst)
          (->> (d/entity db))))))
