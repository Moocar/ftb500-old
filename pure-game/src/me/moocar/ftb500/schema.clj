(ns me.moocar.ftb500.schema
  (:require [schema.core :as s])
  (:import (java.util UUID)))

(def Player
  {:player/id UUID})

(def Seat
  {:seat/id UUID
   :seat/player Player
   :seat/position Number})

(def Card
  {})

(def Deck
  {:deck/num-players Number})

(def bid-names
  #{:bid.name/six-spades
    :bid.name/six-clubs
    :bid.name/six-diamonds
    :bid.name/six-hearts
    :bid.name/six-no-trumps
    :bid.name/seven-spades
    :bid.name/seven-clubs
    :bid.name/seven-diamonds
    :bid.name/seven-hearts
    :bid.name/seven-no-trumps
    :bid.name/eight-spades
    :bid.name/eight-clubs
    :bid.name/eight-diamonds
    :bid.name/eight-hearts
    :bid.name/eight-no-trumps
    :bid.name/nine-spades
    :bid.name/nine-clubs
    :bid.name/nine-diamonds
    :bid.name/nine-hearts
    :bid.name/nine-no-trumps
    :bid.name/ten-spades
    :bid.name/ten-clubs
    :bid.name/ten-diamonds
    :bid.name/ten-hearts
    :bid.name/ten-no-trumps
    :bid.name/misere
    :bid.name/open-misere})

(def BidName
  (apply s/enum bid-names))

(def Bid
  {:bid/name BidName
   :bid/score Number})

(def PlayerBid
  {(s/optional-key :bid) Bid
   :seat Seat})

(def Game
  {:game/seats [Seat]
   :game/first-seat Seat
   :game/deck Deck
   :game.gitty/cards [Card]})
