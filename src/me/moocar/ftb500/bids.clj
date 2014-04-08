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

#_(defn get-passed-seat-ids
  [bids]
  (map (comp :db/id :game.bid/seat)
       (filter pass-bid? bids)))

#_(defn next-seat
  [db game-id seat-id]
  {:pre [db (number? game-id) (number? seat-id)]}
  (-> '[:find ?position
        :in $ ?game-id ?seat-id
        :where [?game-id :game.seat]])
  (let [seat (d/entity db seat-id)
        seat-position (:game.seat)]
    ))

#_(defn not-your-go?
  [db current-bids seat-id]
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
  [db game-id seat-id bid-type-id]
  (let [current-bids (:game/bids (d/entity db game-id))]
    (cond (passed-already? current-bids seat-id)
          "You've passed already"

          #_(not-your-do? db current-bids seat-id))))

(defn add!
  [games game-id seat-id bid-name]
  {:pre [games (number? game-id) (number? seat-id) (keyword? bid-name)]}
  (let [conn (:conn (:db games))
        db (d/db conn)
        bid-type-id (find-bid-id db bid-name)
        current-bids (sort-by :db/id (:game/bids (d/entity db game-id)))]
    (when-let [error (not-valid-bid? db game-id seat-id bid-type-id)]
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
