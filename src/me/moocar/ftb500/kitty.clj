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
  [games seat cards]
  (let [conn (:conn (:db games))
        game (first (:game/_seats seat))
        game-id (:db/id game)
        bids (bids/get-bids game)]
    (cond

     (not (bids/finished? bids (count (:game/seats game))))
     (throw (ex-info "Bidding round not finished!"
                     {:seat seat
                      :cards cards}))

     (exchanged? game)
     (throw (ex-info "Kitty already exchanged!"
                     {:seat seat
                      :cards cards}))

     (not= seat (:game.bid/seat (bids/winning-bid bids)))
     (throw (ex-info "You didn't win the bid"
                     {:seat seat
                      :cards cards})))

    (let [current-kitty (:game.kitty/cards game)
          tx (concat (map #(vector :db/retract game-id
                                   :game.kitty/cards (:db/id %))
                          current-kitty)
                     (map #(vector :db/add game-id
                                   :game.kitty/cards (:db/id %))
                          cards))]
      @(d/transact conn tx))))
