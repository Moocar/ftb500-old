(ns me.moocar.ftb500.bid
  (:require [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.schema :refer [player-bid? bid? seat? game?]]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]))

(defn pass?
  "Returns true if the bid is a pass"
  [player-bid]
  {:pre [(player-bid? player-bid)]}
  (not (contains? player-bid :player-bid/bid)))

(defn finished?
  "Returns true if the bidding round is finished. I.e if 3 players
  have passed"
  [game]
  {:pre [(game? game)]}
  (let [bids (:game/bids game)
        num-players (game/num-players game)]
    (= (dec num-players)
       (count (filter pass? bids)))))

(defn last-bid
  "Returns the last bid. A pass is not a bid"
  [game]
  {:pre [(game? game)]}
  (->> game
       :game/bids
       reverse
       (remove pass?)
       first))

(defn passed?
  "Returns whether the seat has passed already in this bidding round"
  [game seat]
  {:pre [(game? game)
         (seat? seat)]}
  (boolean
   (some #(and (seat= (:player-bid/seat %) seat)
               (pass? %))
         (:game/bids game))))

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
          (if-not (passed? game seat)
            seat
            (recur (seats/next seats seat))))))))

(defn valid?
  "Returns true if the bid is higher than the last bid (not including
  passes). If no bids have been placed, returns true"
  [game bid]
  {:pre [(game? game)
         (bid? bid)]}
  (> (:bid/score bid)
     (get-in (last-bid game) [:player-bid/bid :bid/score] 0)))

(defn winner
  "Returns the winning bid. Expects game to have been finished"
  [game]
  {:pre [(finished? game)]}
  (last-bid game))
