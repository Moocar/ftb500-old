(ns me.moocar.ftb500.game-view
  (:require [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.kitty :as kitty]
            [me.moocar.ftb500.players :as players]))

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

(defn public-game-view
  [game]
  {:pre [game]}
  (let [seats (sort-by :game.seat/position (:game/seats game))
        bids (bids/get-bids game)
        tricks (sort-by :db/id (:game/tricks game))
        kitty-exchanged? (kitty/exchanged? game)]
    {:seats (map view-seat seats)
     :bids (map view-bid bids)
     :winning-bid (bid-name-key (bids/winning-bid bids))
     :kitty-exchanged? (kitty/exchanged? game)
     :tricks (map view-trick tricks)}))

(defn private-view
  [game player]
  {:pre [game player]}
  (let [bids (bids/get-bids game)
        seat (players/get-seat player)
        cards (map card/ext-form (:game.seat/cards seat))
        bidding-finished? (bids/finished? game)
        kitty-exchanged? (kitty/exchanged? game)]
    (cond-> {:cards cards}
            (and bidding-finished? (not kitty-exchanged?)
                 (= seat (:game.bid/seat (bids/winning-bid bids))))
            (assoc :kitty (map card/ext-form (:game.kitty/cards game))))))

(defn view
  [db game player]
  (let [public (public-game-view game)]
    {:public public
     :private (private-view game player)}))
