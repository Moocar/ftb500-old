(ns me.moocar.ftb500.bid
  (:require [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.schema :refer [player-bid? bid? seat? game?]]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]))

(defn pass?
  "Returns true if the bid is a pass"
  [player-bid]
  {:pre [(player-bid? player-bid)]}
  (not (contains? player-bid :player-bid/bid)))

(defn passed?
  "Returns whether the seat has passed already in this bidding round"
  [player-bids seat]
  {:pre [(every? player-bid? player-bids)
         (seat? seat)]}
  (boolean
   (some #(and (seat= (:player-bid/seat %) seat)
               (pass? %))
         player-bids)))

(defn highest-score
  "Returns the score of the highest bid played so far"
  [player-bids]
  {:pre [(every? player-bid? player-bids)]}
  (reduce max
          0
          (keep (comp :bid/score :player-bid/bid)
                player-bids)))

(defn next-seat
  "Finds the next seat that hasn't yet passed. If no bids have been placed,
  returns :game/first-seat. Returns nil if bidding has finished (all
  players have passed)"
  [game]
  {:pre [(game? game)]}
  (let [{:keys [game/seats game/bids]} game
        last-bid-seat (:player-bid/seat (first bids))]
    (if (empty? bids)
      (:game/first-seat game)
      (loop [seat (seats/next seats last-bid-seat)]
        (when-not (seat= seat last-bid-seat)
          (if-not (passed? bids seat)
            seat
            (recur (seats/next seats seat))))))))

(defn finished?
  "Returns true if the bidding round is finished. I.e if 3 players
  have passed"
  [game]
  {:pre [(game? game)]}
  (let [bids (:game/bids game)
        num-players (game/num-players game)]
    (= (dec num-players)
       (count (filter pass? bids)))))

(defn winning-bid
  "Returns the winning bid"
  [game]
  {:pre [(game? game)
         (finished? game)]
   :post [player-bid?]}
  (->> game
       :game/bids
       (remove pass?)
       first))

(defn last-bid?
  "Returns the last bid placed by seat"
  [player-bids seat]
  {:pre [(every? player-bid? player-bids)
         (seat? seat)]}
  (->> player-bids
       (filter #(= (:seat/id (:player-bid/seat %))
                   (:seat/id seat)))
       first
       :player-bid/bid))

(defn find-score
  "Given a bid table and a bid, find the score of the bid. A bid table
  is a seq of {:bid/rank keyword, :bid/suit keyword, :bid/name
  keyword :bid/contract-style :keyword :bid/score Number} ordered by score"
  [bid-table player-bid]
  {:pre [(every? bid? bid-table)
         (player-bid? player-bid)]}
  (->> bid-table
       (filter (fn [bid-table-bid]
                 (= (:bid/name bid-table-bid)
                    (:bid/name (:player-bid/bid player-bid)))))
       first
       :bid/score))

(defn last-non-pass-bid
  "Returns the last bid that was not a pass. Bids is a seq of Bids"
  [player-bids]
  {:pre [(every? player-bid? player-bids)]}
  (first (remove pass? player-bids)))
