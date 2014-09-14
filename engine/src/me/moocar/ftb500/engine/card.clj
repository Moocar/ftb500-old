(ns me.moocar.ftb500.engine.card
  (:require [datomic.api :as d]))

(defn find
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
