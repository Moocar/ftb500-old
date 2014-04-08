(ns me.moocar.ftb500.games
  (:require [com.stuartsierra.component :as component]
            [clojure.pprint :refer [print-table]]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.players :as players]
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
  [game-id game-name hands kitty]
  (concat
   (map #(vector :db/add game-id
                 :game.kitty/cards %)
        kitty)
   (mapcat #(make-seat game-id %1 %2)
           (range)
           hands)
   [[:db/add game-id :game/name game-name]]))

(defn new-game!
  "Should create a deck, shuffle it, deal it to a number of seats,
  save all that to the DB against `game-name`"
  [games game-name num-players]
  {:pre [games (string? game-name) (number? num-players)]}
  (let [db (:db games)
        conn (:conn db)
        game-id (d/tempid :db.part/user)
        deck (shuffle (get-stock-deck-card-ids games num-players))
        {:keys [hands kitty]} (deck/partition-hands deck)
        data (make-game game-id game-name hands kitty)
        result (d/transact conn data)]
    (d/resolve-tempid (d/db conn) (:tempids @result) game-id)))

(defn seat-by-position-q
  []
  '[:find ?seat
    :in $ ?game-id ?position
    :where [?game-id :game/seat ?seat]
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
  (println "joining game" player-id)
  (let [conn (:conn (:db games))
        db (d/db conn)
        seat-id (ffirst (d/q (seat-by-position-q)
                             db game-id position))]
    (assert seat-id)
    (println "seat-id" seat-id)
    (println "plauyer-id" player-id)
    (d/transact conn
                [[:db/add seat-id :game.seat/player player-id]])))

(defn get-game-ids
  [db]
  (-> '[:find ?games
        :where [?games :game/name]]
      (d/q db)
      (->> (map first))))

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
  [games game-id]
  {:pre [games (number? game-id)]}
  (let [db (d/db (:conn (:db games)))
        game (d/entity db game-id)
        game-name (:game/name game)]
    (println
     (format "Game: %s\nHands%sKitty: %s\nBidding:"
             game-name
             (format-seats (:game/seat game))
             (card/format-line-short (:game.kitty/cards game))
             #_(format-bids (:game/bids))))))

(defrecord Games [mode db players]
  component/Lifecycle
  (start [this]
    (when (= :dev mode)
      (let [db (d/db (:conn db))]
        (when (empty? (get-game-ids db))
          (println "Adding new game")
          (let [game-id (new-game! this "Game 1" 4)
                player-ids (players/find-all-ids db)]
            (assert game-id)
            (assert (not (empty? player-ids)))
            (doall
             (map-indexed #(join-game! this game-id %1 %2)
                          player-ids))))))
    this)
  (stop [this]
    this))

(defn new-games
  []
  (component/using
    (map->Games {:mode :dev})
    [:db :players]))
