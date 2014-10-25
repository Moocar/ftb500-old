(ns me.moocar.ftb500.generators
  (:require [clojure.string :as string] 
            [clojure.test.check.generators :as gen])
  (:import (java.util UUID)))

(defn fixed-tuple [size generator]
  (apply gen/tuple (repeat size generator)))

(def char-hex
  (gen/fmap char
            (gen/one-of [(gen/choose 48 57)
                         (gen/choose 65 70)
                         (gen/choose 97 102)])))

(def uuid
  (gen/fmap #(->> (map string/join %)
                  (string/join "-" )
                  (UUID/fromString))
            (gen/tuple (fixed-tuple 8 char-hex)
                       (fixed-tuple 4 char-hex)
                       (fixed-tuple 4 char-hex)
                       (fixed-tuple 4 char-hex)
                       (fixed-tuple 12 char-hex))))

(def player
  (gen/hash-map :player/id uuid))

(def num-players
  (gen/choose 2 4))

(def seat
  (gen/hash-map :seat/id uuid
                :player player
                :seat/position num-players))

(defn deck 
  [player-count]
  (gen/hash-map :deck/num-players (gen/return player-count)))

(def bids
  [{:bid/name :bid.name/six-spades
    :bid/score 40}
   {:bid/name :bid.name/six-clubs
    :bid/score 60}
   {:bid/name :bid.name/six-diamonds
    :bid/score 80}
   {:bid/name :bid.name/six-hearts
    :bid/score 100}
   {:bid/name :bid.name/six-no-trumps
    :bid/score 120}
   {:bid/name :bid.name/seven-spades
    :bid/score 140}
   {:bid/name :bid.name/seven-clubs
    :bid/score 160}
   {:bid/name :bid.name/seven-diamonds
    :bid/score 180}
   {:bid/name :bid.name/seven-hearts
    :bid/score 200}
   {:bid/name :bid.name/seven-no-trumps
    :bid/score 220}
   {:bid/name :bid.name/eight-spades
    :bid/score 240}
   {:bid/name :bid.name/eight-clubs
    :bid/score 260}
   {:bid/name :bid.name/eight-diamonds
    :bid/score 280}
   {:bid/name :bid.name/eight-hearts
    :bid/score 300}
   {:bid/name :bid.name/eight-no-trumps
    :bid/score 320}
   {:bid/name :bid.name/nine-spades
    :bid/score 340}
   {:bid/name :bid.name/nine-clubs
    :bid/score 360}
   {:bid/name :bid.name/nine-diamonds
    :bid/score 380}
   {:bid/name :bid.name/nine-hearts
    :bid/score 400}
   {:bid/name :bid.name/nine-no-trumps
    :bid/score 420}
   {:bid/name :bid.name/ten-spades
    :bid/score 440}
   {:bid/name :bid.name/ten-clubs
    :bid/score 460}
   {:bid/name :bid.name/ten-diamonds
    :bid/score 480}
   {:bid/name :bid.name/ten-hearts
    :bid/score 500}
   {:bid/name :bid.name/ten-no-trumps
    :bid/score 500}
   {:bid/name :bid.name/misere
    :bid/score 250}
   {:bid/name :bid.name/open-misere
    :bid/score 500}])

(def bid-names
  (gen/fmap :bid/name (gen/elements bids)))

(def bid-name
  (gen/elements bid-names))

(def bid
  (gen/elements bids))

(def player-bid
  (gen/bind gen/boolean
            (fn [v]
              (if v
                (gen/hash-map :seat seat
                              :bid bid)
                (gen/hash-map :seat seat)))))

(def card
  (gen/hash-map :card gen/int))

(defn positions [num-players]
  (range num-players))

(defn seats 
  [num-players]
  (gen/bind (fixed-tuple num-players player)
            (fn [players]
              (gen/fmap (fn [seats]
                          (map #(assoc %1 :player %2 :seat/position %3)
                               seats
                               players
                               (range (count players))))
                        (fixed-tuple (count players) seat)))))

(def game
  (gen/bind (gen/bind (gen/bind num-players seats)
                      (fn [seats]
                        (gen/tuple (gen/return seats)
                                   (deck (count seats)))))
            (fn [[seats deck]]
              (gen/hash-map :game/seats (gen/return seats)
                            :game/first-seat (gen/elements seats)
                            :game/deck (gen/return deck)
                            :game.kitty/cards (gen/return 4)))))
