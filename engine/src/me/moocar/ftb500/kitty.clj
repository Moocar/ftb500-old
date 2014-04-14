(ns me.moocar.ftb500.kitty
  (:require [datomic.api :as d]
            [me.moocar.ftb500.bids :as bids]))

(defn exchanged?
  [game]
  (let [db (d/entity-db game)
        num-players (count (:game/seats game))]
    (-> '[:find ?cards ?tx ?added
          :in $ ?game
          :where [?game :game.kitty/cards ?cards ?tx ?added]]
        (d/q (d/history db) (:db/id game))
        (count)
        (not= 3))))

(defn exchange!
  [conn seat cards]
  (let [game (first (:game/_seats seat))
        game-id (:db/id game)
        bids (bids/get-bids game)]
    (if-let [error (cond

                    (not= (count cards) 3)
                    "Must submit 3 cards"

                    (not (bids/finished? bids (count (:game/seats game))))
                    "Bidding round not finished!"

                    (exchanged? game)
                    "Kitty already exchanged!"

                    (not= seat (:game.bid/seat (bids/winning-bid bids)))
                    "You didn't win the bid")]
      {:error {:msg error
               :data {:seat (:game.seat/position seat)}}}
      (let [current-kitty (:game.kitty/cards game)
            tx (concat (map #(vector :db/retract game-id
                                     :game.kitty/cards (:db/id %))
                            current-kitty)
                       (map #(vector :db/add game-id
                                     :game.kitty/cards (:db/id %))
                            cards))]
        @(d/transact conn tx)))))
