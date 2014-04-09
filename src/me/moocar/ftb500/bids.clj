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
  [current-bids seat-id]
  (some #(and (= (:db/id (:game.bid/seat %))
                 seat-id)
              (pass-bid? %))
        current-bids))

(defn score-too-low?
  [current-bids bid-type]
  (< (:bid/score bid-type)
     (reduce max
             0
             (map (comp :bid/score :game.bid/bid)
                  current-bids))))

#_(defn get-passed-seat-ids
  [bids]
  (map (comp :db/id :game.bid/seat)
       (filter pass-bid? bids)))

(defn get-next-seat
  [game-seats seat]
  {:pre [game-seats seat]}
  (let [next-seat-position (mod (inc (:game.seat/position seat)) (count game-seats))]
    (->> game-seats
         (filter #(= next-seat-position (:game.seat/position %)))
         (first))))

(defn find-next-seat
  [db seat-id]
  {:pre [db (number? seat-id)]}
  (let [seat (d/entity db seat-id)
        game (first (:game/_seats seat))
        seats (:game/seats game)]
    (get-next-seat seats seat)))

#_(defn not-your-go?
  [db current-bids seat-id]
  (let [seat (d/entity db seat-id)
        game (first (:game/_seats seat))
        seats (:game/seats game)]
   (loop [next-seat (find-next-seat db (:db/id (last current-bids)))]
     (if (= (:db/id next-seat) seat-id)
       false
       (if (passed-already? current-bids next-seat)
         (recur (get-next-seat db ))))))
  (let [passed-seat-ids (get-passed-seat-ids current-bids)
        last-seat-id (last-seat db seat-id)
        reverse-bids (reverse current-bids)
        last-bid (first (remove pass-bid? reverse-bids))]
    (first (map (fn [bid]
                  ())
                (reverse current-bids)))
    (->> (reverse current-bids)
         (remove pass-bid?)
         (last-seat )))
  (first ()))

(defn not-valid-bid?
 [db game-id seat-id bid-type]
 (let [current-bids (:game/bids (d/entity db game-id))]
   (and (> (count current-bids) 0)
       (cond (passed-already? current-bids seat-id)
             "You've passed already"

             (pass-bid? {:game.bid/bid bid-type})
             false

             (score-too-low? current-bids bid-type)
             "Bid too low"

             #_(not-your-go? db current-bids seat-id)))))

(defn add!
  [games game-id seat-id bid-name]
  {:pre [games (number? game-id) (number? seat-id) (keyword? bid-name)]}
  (let [conn (:conn (:db games))
        db (d/db conn)
        bid-type-id (find-bid-id db bid-name)
        bid-type (d/entity db bid-type-id)
        current-bids (sort-by :db/id (:game/bids (d/entity db game-id)))]
    (when-let [error (not-valid-bid? db game-id seat-id bid-type)]
      (throw (ex-info error
                      {:seat seat-id
                       :bid-type-name bid-name})))
    (let [bid-id (d/tempid :db.part/user)
          tx [{:db/id bid-id
               :game.bid/bid bid-type-id
               :game.bid/seat seat-id}
              {:db/id game-id
               :game/bids bid-id}]]
      @(d/transact conn tx))))
