(ns me.moocar.ftb500.bid
  (:require [me.moocar.ftb500.seats :as seats]))

(defn pass?
  "Returns true if the bid is a pass"
  [game-bid]
  (= :bid.name/pass
     (:bid/name (:bid game-bid))))

(defn passed-already?
  "Returns whether the seat has passed already in this bidding round"
  [game-bids seat]
  (some #(and (= (:seat %)
                 seat)
              (pass? %))
        game-bids))

(defn positive-score?
  [game-bids bid]
  (> (:bid/score bid)
     (reduce max 0 (map (comp :bid/score :bid) game-bids))))

#_(defn your-go?
  [game game-bids seat]
  (or (and (empty? game-bids)
           (first-player? game seat))
      (and (not (passed-already? game-bids seat))
           (let [game-seats (:game/seats game)
                 last-seat (:seat (last game-bids))]
             (loop [next-seat (seats/next game-seats last-seat)]
               (or (= next-seat seat)
                   (when (passed-already? game-bids next-seat)
                     (recur (seats/next game-seats next-seat)))))))))

(defn finished?
  [game game-bids]
  {:pre [(sequential? game-bids) game]}
  (let [num-players (:game/num-players game)]
    (= (dec num-players) (count (filter pass? game-bids)))))

(defn winning-bid
  [game-bids]
  (->> game-bids
       (reverse)
       (remove pass?)
       (first)))

#_{:bid {:bid/name :bid.name/six-spades}
   :seat {:seat/id "sdf"}}

#_(defn last-non-pass-bid
  [bids]
  (->> bids
       (remove #(= :pass (:bid/name %)))
       first
       :bid
       bid-scores))
