(ns me.moocar.ftb500.bid
  (:require [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.schema :refer [player-bid? bid? seat? game?]]
            [me.moocar.ftb500.seats :as seats]))

(defn pass?
  "Returns true if the bid is a pass"
  [player-bid]
  {:pre [(player-bid? player-bid)]}
  (not (contains? player-bid :player-bid/bid)))

(defn passed-already?
  "Returns whether the seat has passed already in this bidding round"
  [player-bids seat]
  {:pre [(every? player-bid? player-bids)
         (seat? seat)]}
  (boolean
   (some #(and (= (:seat/id (:player-bid/seat %))
                  (:seat/id seat))
               (pass? %))
         player-bids)))

(defn positive-score?
  [player-bids bid]
  {:pre [(every? player-bid? player-bids)
         (bid? bid)]}
  (> (:bid/score bid)
     (reduce max 0 (keep (comp :bid/score :player-bid/bid) player-bids))))

(defn your-go?
  [game seats player-bids seat]
  {:pre [(game? game)
         (every? seat? seats)
         (every? player-bid? player-bids)
         (seat? seat)]}
  (or (and (empty? player-bids)
           (game/first-player? game seat))
      (and (not (passed-already? player-bids seat))
           (let [last-seat (:player-bid/seat (first player-bids))]
             (loop [next-seat (seats/next seats last-seat)]
               (or (seats/seat= next-seat seat)
                   (when (passed-already? player-bids next-seat)
                     (recur (seats/next seats next-seat)))))))))

(defn finished?
  [game player-bids]
  {:pre [(game? game)
         (every? player-bid? player-bids)]}
  (let [num-players (game/num-players game)]
    (= (dec num-players) (count (filter pass? player-bids)))))

(defn winning-bid
  [player-bids]
  {:pre [(every? player-bid? player-bids)]}
  (->> player-bids
       (remove pass?)
       (first)))

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
