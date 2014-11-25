(ns me.moocar.ftb500.score
  (:require [me.moocar.ftb500.bid :as bid] 
            [me.moocar.ftb500.schema :refer [trick-game? trick?]]
            [me.moocar.ftb500.seats :as seats :refer [seat= in-team?]]
            [me.moocar.ftb500.trick :as trick]))

(defn- add-winner-to-tricks
  "Adds :trick/winner to each trick"
  [{:keys [game/tricks] :as game}]
  {:pre [(trick-game? game)]}
  (map (fn [trick]
         (assoc trick
           :trick/winner (trick/winner game trick)))
       tricks))

(defn- winning-team
  [teams trick]
  (let [seat (:trick.play/seat (:trick/winner trick))]
    (first (filter (fn [team]
                     (first (filter #(seat= seat %) team)))
                   teams))))

(defn summary
  [game]
  [{:pre [(trick-game? game)]}]
  (let [{:keys [game/tricks game/bids]} game]
    (let [tricks (add-winner-to-tricks game)
          winning-bid (bid/winner game)
          teams (seats/teams game)
          winners (group-by #(winning-team teams %) tricks)]
      (reduce-kv
       (fn [m team won-tricks]
         (let [bid-score (:bid/score (:player-bid/bid winning-bid))
               won-bidding? (in-team? team (:player-bid/seat winning-bid))]
           (assoc m team
                  {:score (if won-bidding?
                            (if (>= (count won-tricks) (:bid/tricks (:player-bid/bid winning-bid)))
                              bid-score
                              (- bid-score))
                            (* 10 (count won-tricks)))})))
       {}
       winners))))
