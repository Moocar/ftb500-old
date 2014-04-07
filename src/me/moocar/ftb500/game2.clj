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
     (map (fn [seat]
            {:position (:game.seat/position seat)
             :cards (card/format-line-short (:game.seat/cards seat))})
          seats))))

(defn print-game
  [games ent-id]
  (let [db (d/db (:conn (:db games)))
        entity (d/entity db ent-id)
        game-name (:game/name entity)]
    (format "Game: %s\nHands%sKitty: %s"
            game-name
            (format-seats (:game/seat entity))
            (card/format-line-short (:game.kitty/cards entity)))))

(defrecord Games [db])

(defn new-games
  []
  (component/using
    (map->Games {})
    [:db]))
