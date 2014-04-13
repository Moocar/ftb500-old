(ns me.moocar.ftb500.games
  (:require [com.stuartsierra.component :as component]
            [clojure.pprint :refer [print-table]]
            [clojure.string :as string]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.kitty :as kitty]
            [me.moocar.ftb500.players :as players]
            [me.moocar.ftb500.tricks :as tricks]
            [datomic.api :as d]))

(defn seat-by-position-q
  []
  '[:find ?seat
    :in $ ?game-id ?position
    :where [?game-id :game/seats ?seat]
    [?seat :game.seat/position ?position]])

(defn find-seat-by-position
  [games game-id position]
  {:pre [games (number? game-id) (number? position)]}
  (let [conn (:conn (:db games))
        db (d/db conn)]
    (ffirst (d/q (seat-by-position-q)
                 db game-id position))))

(defn join-game!
  [games game-id position player-id]
  {:pre [games (number? game-id) (number? position) (number? player-id)]}
  (let [conn (:conn (:db games))
        db (d/db conn)
        seat-id (ffirst (d/q (seat-by-position-q)
                             db game-id position))]
    (assert seat-id)
    @(d/transact conn
                 [[:db/add seat-id :game.seat/player player-id]])))

(defn get-game-ids
  [db]
  (-> '[:find ?games
        :where [?games :game/name]]
      (d/q db)
      (->> (map first))))

#_(defrecord Games [mode db players]
  component/Lifecycle
  (start [this]
    (when (= :dev mode)
      (let [conn (:conn db)
            db (d/db conn)]
        (when (empty? (get-game-ids db))
          (println "Adding new game")
          (let [game-id (new-game! conn 4)
                game (d/entity (d/db conn) game-id)
                player-ids (players/find-all-ids db)
                seats (->> (:game/seats (d/entity (d/db conn) game-id))
                           (sort-by :db/id))]
            (assert game-id)
            (assert (not (empty? player-ids)))
            (doall
             (map-indexed #(join-game! this game-id %1 %2)
                          player-ids))

            ;; Bids
            (bids/add! this game (first seats) :six-clubs)
            (bids/add! this game (second seats) :seven-hearts)
            (bids/add! this game (nth seats 2) :pass)
            (bids/add! this game (nth seats 3) :eight-clubs)
            (bids/add! this game (nth seats 0) :pass)
            (bids/add! this game (nth seats 1) :eight-hearts)
            (bids/add! this game (nth seats 3) :pass)

            (let [winning-seat (d/entity (d/db conn) (:db/id (nth seats 1)))
                  cards-to-exchange (take 3 (:game.seat/cards winning-seat))]
              (kitty/exchange! this (d/entity (d/db conn) (:db/id winning-seat)) cards-to-exchange)
              (tricks/add-play! this
                                (d/entity (d/db conn) (:db/id (second seats)))
                                (nth (vec (:game.seat/cards winning-seat)) 3))
              (let [game (d/entity (d/db conn) (:db/id game))
                    winning-suit (:bid/suit (:game.bid/bid
                                             (bids/winning-bid (:game/bids game))))
                    next-seat (d/entity (d/db conn) (:db/id (nth seats 2)))
                    next-card (nth (vec (:game.seat/cards (nth seats 2))) 0)]
                (tricks/add-play! this next-seat next-card)
                (let [db (d/db conn)
                      game (d/entity db (:db/id game))
                      next-seat (d/entity db (:db/id (nth seats 3)))
                      next-card (nth (vec (:game.seat/cards (nth seats 3))) 0)]
                  (tricks/add-play! this next-seat next-card))))))))
    this)
  (stop [this]
    this))

#_(defn new-games-component
  []
  (component/using
    (map->Games {:mode :dev})
    [:db :players]))
