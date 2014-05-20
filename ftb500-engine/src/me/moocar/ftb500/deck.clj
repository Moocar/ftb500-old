(ns me.moocar.ftb500.deck
  (:require [datomic.api :as d])
  (:refer-clojure :exclude [find]))

(defn partition-hands
  [deck]
  (let [partitions (partition-all 10 deck)]
    {:hands (take 4 partitions)
     :kitty (last partitions)}))

(defn find
  [db num-players]
  (-> '[:find ?deck
        :in $ ?num-players
        :where [?deck :deck/num-players ?num-players]]
      (d/q db num-players)
      ffirst
      (->> (d/entity db))))
