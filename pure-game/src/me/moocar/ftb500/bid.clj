(ns me.moocar.ftb500.bid
  (:require [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.schema :refer [PlayerBid Bid Seat Game]]
            [me.moocar.ftb500.seats :as seats]
            [schema.core :as s]))

(defn pass?
  "Returns true if the bid is a pass"
  [player-bid]
  {:pre [(s/validate PlayerBid player-bid)]}
  (not (contains? player-bid :bid)))

(defn passed-already?
  "Returns whether the seat has passed already in this bidding round"
  [player-bids seat]
  {:pre [(s/validate [PlayerBid] player-bids)
         (s/validate Seat seat)]}
  (boolean
   (some #(and (= (:seat/id (:seat %))
                  (:seat/id seat))
               (pass? %))
         player-bids)))

(defn positive-score?
  [player-bids bid]
  {:pre [(s/validate [PlayerBid] player-bids)
         (s/validate Bid bid)]}
  (> (:bid/score bid)
     (reduce max 0 (map (comp :bid/score :bid) player-bids))))

(defn your-go?
  [game seats player-bids seat]
  {:pre [(s/validate Game game)
         (s/validate [Seat] seat)
         (s/validate [PlayerBid] player-bids)
         (s/validate Seat seat)]}
  (or (and (empty? player-bids)
           (game/first-player? game seat))
      (and (not (passed-already? player-bids seat))
           (let [last-seat (:seat (first player-bids))]
             (loop [next-seat (seats/next seats last-seat)]
               (or (seats/seat= next-seat seat)
                   (when (passed-already? player-bids next-seat)
                     (recur (seats/next seats next-seat)))))))))

(defn finished?
  [game player-bids]
  {:pre [(s/validate Game game)
         (s/validate [PlayerBid] player-bids)]}
  (let [num-players (game/num-players game)]
    (= (dec num-players) (count (filter pass? player-bids)))))

(defn winning-bid
  [player-bids]
  {:pre [(s/validate [PlayerBid] player-bids)]}
  (->> player-bids
       (reverse)
       (remove pass?)
       (first)))

(defn last-bid?
  "Returns the last bid placed by seat"
  [player-bids seat]
  {:pre [(s/validate [PlayerBid] player-bids)
         (s/validate Seat seat)]}
  (->> player-bids
       (filter #(= (:seat/id (:seat %))
                   (:seat/id seat)))
       first
       :bid))

(defn find-score
  "Given a bid table and a bid, find the score of the bid. A bid table
  is a seq of {:bid/rank keyword, :bid/suit keyword, :bid/name
  keyword :bid/contract-style :keyword :bid/score Number} ordered by score"
  [bid-table player-bid]
  {:pre [(s/validate [Bid] bid-table)
         (s/validate PlayerBid player-bid)]}
  (->> bid-table
       (filter (fn [bid-table-bid]
                 (= (:bid/name bid-table-bid)
                    (:bid/name (:bid player-bid)))))
       first
       :bid/score))

(defn last-non-pass-bid
  "Returns the last bid that was not a pass. Bids is a seq of Bids"
  [player-bids]
  {:pre [(s/validate [PlayerBid] player-bids)]}
  (first (remove pass? player-bids)))
