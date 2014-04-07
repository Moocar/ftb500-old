(ns me.moocar.ftb500.game2
  (:require [com.stuartsierra.component :as component]
            [clojure.pprint :refer [print-table]]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.deck :as deck]
            [datomic.api :as d]))

(defn get-stock-deck-card-ids
  [games num-players]
  (let [db (:db games)]
    (->> (d/q '[:find ?cards
                :in $ ?num-players
                :where [?deck :deck/cards ?cards]
                [?deck :deck/num-players ?num-players]]
              (d/db (:conn db))
              num-players)
         (map first))))

(defn make-seat
  [game-id index card-ids]
  (let [seat-id (d/tempid :db.part/user)]
    (concat
     (map #(vector :db/add seat-id
                   :game.seat/cards %)
          card-ids)
     [[:db/add seat-id :game.seat/position index]
      [:db/add game-id :game/seat seat-id]])))

(defn make-game
  [game-name hands kitty]
  (let [game-id (d/tempid :db.part/user)]
    (concat
     (map #(vector :db/add game-id
                   :game.kitty/cards %)
          kitty)
     (mapcat #(make-seat game-id %1 %2)
             (range)
             hands)
     [[:db/add game-id :game/name game-name]])))

(defn new-game!
  "Should create a deck, shuffle it, deal it to a number of seats,
  save all that to the DB against `game-name`"
  [games game-name num-players]
  (let [db (:db games)
        game-id (d/tempid :db.part/user)
        deck (shuffle (get-stock-deck-card-ids games num-players))
        {:keys [hands kitty]} (deck/partition-hands deck)
        data (make-game game-name hands kitty)]
    (d/transact (:conn db)
                data)))

(defn add-player!
  [games player-name]
  (let [conn (:conn (:db games))
        player-id (d/tempid :db.part/user)
        result (d/transact conn
                           [[:db/add player-id :player/name player-name]])]
    (d/resolve-tempid (d/db conn) (:tempids @result) player-id)))

(defn get-players-ids
  [games]
  (let [conn (:conn (:db games))
        db (d/db conn)]
    (map first
         (d/q '[:find ?player
                :where [?player :player/name]]
              db))))

(defn format-player
  [games player-id]
  (let [db (d/db (:conn (:db games)))
        player (d/entity db player-id)]
    (:player/name player)))

(defn seat-by-position-q
  []
  '[:find ?seat
    :in $ ?game-id ?position
    :where [?game-id :game/seat ?seat]
    [?seat :game.seat/position ?position]])

(defn find-seat-by-position
  [games game-id position]
  (let [conn (:conn (:db games))
        db (d/db conn)]
    (ffirst (d/q (seat-by-position-q)
                 db game-id position))))

(defn join-game!
  [games game-id position player-id]
  (let [conn (:conn (:db games))
        db (d/db conn)
        seat-id (ffirst (d/q (seat-by-position-q)
                             db game-id position))]
    (d/transact conn
                [[:db/add seat-id :game.seat/player player-id]])))

(defn add-bid!
  [games game-id player-id bid-id])

(defn get-game-ids
  [games]
  (let [db (d/db (:conn (:db games)))]
    (->> (d/q '[:find ?games
                :where [?games :game/name]]
              db)
         (map first))))

(defn format-seats
  [seats]
  (with-out-str
    (print-table
     (->> seats
          (sort-by :game.seat/position)
          (map (fn [seat]
                 {:name (:player/name (:game.seat/player seat))
                  :cards (card/format-line-short (:game.seat/cards seat))}))))))

(defn print-game
  [games ent-id]
  (let [db (d/db (:conn (:db games)))
        entity (d/entity db ent-id)
        game-name (:game/name entity)]
    (format "Game: %s\nHands%sKitty: %s\nBidding:"
            game-name
            (format-seats (:game/seat entity))
            (card/format-line-short (:game.kitty/cards entity))
            #_(format-bids (:game/bids)))))

(defrecord Games [db])

(defn new-games
  []
  (component/using
    (map->Games {})
    [:db]))
