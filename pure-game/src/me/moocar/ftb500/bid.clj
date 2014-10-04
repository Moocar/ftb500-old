(ns me.moocar.ftb500.bid
  (:require [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.game :as game]))

(defn pass?
  "Returns true if the bid is a pass"
  [game-bid]
  {:pre [(:bid game-bid)
         (keyword? (:bid/name (:bid game-bid)))]}
  (= :bid.name/pass
     (:bid/name (:bid game-bid))))

(defn passed-already?
  "Returns whether the seat has passed already in this bidding round"
  [bids seat]
  (boolean
   (some #(and (= (:seat/id (:seat %))
                  (:seat/id seat))
               (pass? %))
         bids)))

(defn positive-score?
  [game-bids bid]
  (> (:bid/score bid)
     (reduce max 0 (map (comp :bid/score :bid) game-bids))))

(defn your-go?
  [game seats bids seat]
  {:pre [(sequential? seats)
         (or (empty? bids) (:seat/position (:seat (first bids))))
         (:seat/id seat)]}
  (or (and (empty? bids)
           (game/first-player? game seat))
      (and (not (passed-already? bids seat))
           (let [last-seat (:seat (first bids))]
             (loop [next-seat (seats/next seats last-seat)]
               (or (seats/seat= next-seat seat)
                   (when (passed-already? bids next-seat)
                     (recur (seats/next seats next-seat)))))))))

(defn finished?
  [game bids]
  {:pre [(sequential? bids)
         game]}
  (let [num-players (game/num-players game)]
    (= (dec num-players) (count (filter pass? bids)))))

(defn winning-bid
  [game-bids]
  {:pre [(sequential? game-bids)]}
  (->> game-bids
       (reverse)
       (remove pass?)
       (first)))

(defn last-bid?
  "Returns the last bid placed by seat"
  [bids seat]
  (->> bids
       (filter #(= (:seat/id (:seat %))
                   (:seat/id seat)))
       first
       :bid))

(defn find-score
  "Given a bid table and a bid, find the score of the bid. A bid table
  is a seq of {:bid/rank keyword, :bid/suit keyword, :bid/name
  keyword :bid/contract-style :keyword :bid/score Number} ordered by score"
  [bid-table bid]
  {:pre [(not-empty bid-table)
         (number? (:bid/score (first bid-table)))
         (keyword? (:bid/name (first bid-table)))]}
  (->> bid-table
       (filter (fn [bid-table-bid]
                 (= (:bid/name bid-table-bid)
                    (:bid/name (:bid bid)))))
       first
       :bid/score))

#_{:bid {:bid/name :bid.name/six-spades}
   :seat {:seat/id "sdf"}}

(defn pass?
  "Returns true if the bid is a pass"
  [game-bid]
  {:pre [(:bid game-bid)
         (keyword? (:bid/name (:bid game-bid)))]}
  (= :bid.name/pass
     (:bid/name (:bid game-bid))))

(defn last-non-pass-bid
  "Returns the last bid that was not a pass. Bids is a seq of Bids"
  [bids]
  (first (remove pass? bids)))
