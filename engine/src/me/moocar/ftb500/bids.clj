(ns me.moocar.ftb500.bids
  (:require [clojure.pprint :refer [print-table]]
            [datomic.api :as d]
            [me.moocar.ftb500.seats :as seats]))

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

(defn not-your-go?
  [current-bids seat]
  (let [game (first (:game/_seats seat))
        game-seats (:game/seats game)]
    (loop [next-seat (seats/next game-seats (:game.bid/seat (last current-bids)))]
      (if (= next-seat seat)
        false
        (if (passed-already? current-bids next-seat)
          (recur (seats/next game-seats next-seat))
          true)))))

(defn finished?
  [bids num-players]
  {:pre [(sequential? bids)]}
  (= (dec num-players) (count (filter pass-bid? bids))))

(defn not-valid-bid?
 [current-bids seat bid-type num-players]
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
       "It's not your go!"

       (finished? current-bids num-players)
       "Bidding round is finished!")))

(defn get-bids
  [game]
  (sort-by :db/id (:game/bids game)))

(defn winning-bid
  [bids]
  (->> bids
       (reverse)
       (remove pass-bid?)
       (first)))

(defn add!
  [conn game seat bid-name]
  {:pre [conn game seat (keyword? bid-name)]}
  (let [db (d/db conn)
        bid-type-id (find-bid-id db bid-name)
        bid-type (d/entity db bid-type-id)
        current-bids (get-bids game)]
    (if-let [error (not-valid-bid? current-bids seat bid-type
                                   (count (:game/seats game)))]
      {:error {:msg error
               :data {:seat (:game.seat/position seat)
                      :bid-type-name bid-name}}}
      (let [bid-id (d/tempid :db.part/user)
            tx [{:db/id bid-id
                 :game.bid/bid bid-type-id
                 :game.bid/seat (:db/id seat)}
                {:db/id (:db/id game)
                 :game/bids bid-id}]]
        @(d/transact conn tx)))))
