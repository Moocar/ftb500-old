(ns me.moocar.ftb500.game-view
  (:require [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.kitty :as kitty]))

(defn bid-name-key
  [bid]
  (keyword (name (:bid/name (:game.bid/bid bid)))))

(defn seat-player-name
  [seat]
  (:player/name (:game.seat/player seat)))

(defn view-play
  [play]
  {:card (card/ext-form (:trick.play/card play))
   :player (seat-player-name (:trick.play/seat play))})

(defn view-trick
  [trick]
  (let [plays (sort-by :db/id (:game.trick/plays trick))]
    {:plays (map view-play plays)}))

(defn view-bid
  [bid]
  {:player (seat-player-name (:game.bid/seat bid))
   :bid (bid-name-key bid)})

(defn view-seat
  [seat]
  {:position (:game.seat/position seat)
   :player (seat-player-name seat)
   :num-cards (count (:game.seat/cards seat))})

(defn view
  [db game player]
  (let [seats (sort-by :game.seat/position (:game/seats game))
        bids (sort-by :db/id (:game/bids game))
        tricks (sort-by :db/id (:game/tricks game))]
    {:seats (map view-seat seats)
     :bids (map view-bid bids)
     :winning-bid (bid-name-key (bids/winning-bid bids))
     :kitty-exchanged? (kitty/exchanged? game)
     :tricks (map view-trick tricks)}))
