(ns me.moocar.ftb500.bids
  (:require [clojure.pprint :refer [print-table]]
            [datomic.api :as d]))

(defn dbg-bid-table
  [db]
  (-> '[:find ?bid
        :where [?bid :bid/name]]
      (d/q db)
      (->> (map (comp #(d/entity db %)
                      first))
           (sort-by :bid/score)
           (print-table))))

(defn find-bid-id
  [db bid-name]
  (-> '[:find ?bid
        :in $ ?bid-name
        :where [?bid :bid/name ?bid-name]]
      (d/q db (keyword "bid.name" (name bid-name)))
      ffirst))

(defn pass-bid?
  [bid]
  (= :bid.name/pass
     (:bid/name (:game.bid/bid bid))))

(defn passed-already?
  [current-bids seat]
  (some #(and (= (:game.bid/seat %) seat)
              (pass-bid? %))
        current-bids))

(defn score-too-low?
  [current-bids bid-type]
  (< (:bid/score bid-type)
     (reduce max
             0
             (map (comp :bid/score :game.bid/bid)
                  current-bids))))

(defn get-next-seat
  [game-seats seat]
  {:pre [game-seats seat]}
  (let [next-seat-position (mod (inc (:game.seat/position seat))
                                (count game-seats))]
    (->> game-seats
         (filter #(= next-seat-position (:game.seat/position %)))
         (first))))

(defn not-your-go?
  [current-bids seat]
  (let [game (first (:game/_seats seat))
        game-seats (:game/seats game)]
   (loop [next-seat (get-next-seat game-seats (:game.bid/seat (last current-bids)))]
     (if (= next-seat seat)
       false
       (if (passed-already? current-bids next-seat)
         (recur (get-next-seat game-seats next-seat))
         true)))))

(defn not-valid-bid?
 [current-bids seat bid-type]
 {:pre [(sequential? current-bids) seat bid-type]}
 (and (> (count current-bids) 0)
      (cond

       (passed-already? current-bids seat)
       "You've passed already"

       (pass-bid? {:game.bid/bid bid-type})
       false

       (score-too-low? current-bids bid-type)
       "Bid too low"

       (not-your-go? current-bids seat)
       "It's not your go!")))

(def finished?
  (memoize
   (fn [bids num-players]
     {:pre [(sequential? bids)]}
     (= (dec num-players) (count (filter pass-bid? bids))))))

(defn get-current-bids
  [game]
  (sort-by :db/id (:game/bids game)))

(defn add!
  [games game-id seat bid-name]
  {:pre [games (number? game-id) seat (keyword? bid-name)]}
  (let [conn (:conn (:db games))
        db (d/db conn)
        bid-type-id (find-bid-id db bid-name)
        bid-type (d/entity db bid-type-id)
        game (d/entity db game-id)
        current-bids (get-current-bids game)]
    (when-let [error (not-valid-bid? current-bids seat bid-type)]
      (throw (ex-info error
                      {:seat (:game.seat/position seat)
                       :bid-type-name bid-name})))
    (let [bid-id (d/tempid :db.part/user)
          tx [{:db/id bid-id
               :game.bid/bid bid-type-id
               :game.bid/seat (:db/id seat)}
              {:db/id game-id
               :game/bids bid-id}]]
      @(d/transact conn tx))))
