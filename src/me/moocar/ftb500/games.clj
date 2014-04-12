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

(defn find-deck
  [db num-players]
  (-> '[:find ?deck
        :in $ ?num-players
        :where [?deck :deck/num-players ?num-players]]
      (d/q db num-players)
      ffirst
      (->> (d/entity db))))

(defn make-seat
  [game-id index card-ids]
  (let [seat-id (d/tempid :db.part/user)]
    (concat
     (map #(hash-map :db/id seat-id
                     :game.seat/cards (:db/id %))
          card-ids)
     [{:db/id seat-id
       :game.seat/position index}
      {:db/id game-id
       :game/seats seat-id}])))

(defn make-game
  [game-id deck game-name hands kitty]
  (concat
   (map #(hash-map :db/id game-id
                   :game.kitty/cards (:db/id %))
        kitty)
   (mapcat #(make-seat game-id %1 %2)
           (range)
           hands)
   [{:db/id game-id
     :game/name game-name
     :game/deck (:db/id deck)}]))

(defn new-game!
  "Should create a deck, shuffle it, deal it to a number of seats,
  save all that to the DB against `game-name`"
  [games game-name num-players]
  {:pre [games (string? game-name) (number? num-players)]}
  (let [conn (:conn (:db games))
        db (d/db conn)
        game-id (d/tempid :db.part/user)
        deck (find-deck db num-players)
        deck-cards (shuffle (:deck/cards deck))
        {:keys [hands kitty]} (deck/partition-hands deck-cards)
        data (make-game game-id deck game-name hands kitty)
        result @(d/transact conn data)]
    (d/resolve-tempid (d/db conn) (:tempids result) game-id)))

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

(defn format-seats
  [seats]
  (with-out-str
    (print-table
     (->> seats
          (sort-by :game.seat/position)
          (map (fn [seat]
                 {:db/id (:db/id seat)
                  :name (:player/name (:game.seat/player seat))
                  :cards (card/format-line-short (:game.seat/cards seat))}))))))

(defn format-bids
  [bids]
  (->> bids
       (sort-by :db/id)
       (map #(hash-map :player (:player/name (:game.seat/player (:game.bid/seat %)))
                       :bid (name (:bid/name (:game.bid/bid %)))))
       (print-table)
       (with-out-str)))

(defn format-kitty-exchanged?
  [game]
  (if (kitty/exchanged? game)
    "Kitty Exchanged\n"
    ""))

(defn format-play
  [play]
  (str (card/format-short (:trick.play/card play))
       "(" (:player/name (:game.seat/player (:trick.play/seat play))) ")"))

(defn format-trick
  [index trick]
  (str (inc index) " "
       (->> (:game.trick/plays trick)
            (map format-play)
            (string/join ","))))

(defn format-tricks
  [game]
  (let [tricks (:game/tricks game)]
    (if-not (empty? tricks)
      (str "Tricks:\n"
           (->> tricks
                (map-indexed format-trick)
                (string/join "\n")))
      "")))

(defn print-game
  [games game-id]
  {:pre [games (number? game-id)]}
  (let [db (d/db (:conn (:db games)))
        game (d/entity db game-id)
        game-name (:game/name game)]
    (println
     (format "%s (%s)%sKitty: %s\nBidding:%s%s%s"
             game-name
             game-id
             (format-seats (:game/seats game))
             (card/format-line-short (:game.kitty/cards game))
             (format-bids (:game/bids game))
             (format-kitty-exchanged? game)
             (format-tricks game)))))

(defrecord Games [mode db players]
  component/Lifecycle
  (start [this]
    (when (= :dev mode)
      (let [conn (:conn db)
            db (d/db conn)]
        (when (empty? (get-game-ids db))
          (println "Adding new game")
          (let [game-id (new-game! this "Game 1" 4)
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
                                             (bids/winning-bid (:game/bids game))))]))))))
    this)
  (stop [this]
    this))

(defn new-games-component
  []
  (component/using
    (map->Games {:mode :dev})
    [:db :players]))
