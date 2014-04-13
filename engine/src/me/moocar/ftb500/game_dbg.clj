(ns me.moocar.ftb500.game-dbg
  (:require [clojure.pprint :refer [print-table]]
            [clojure.string :as string]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.kitty :as kitty]))

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
  [db game]
  {:pre [db game]}
  (println
   (format "%s%sKitty: %s\nBidding:%s%s%s"
           (:db/id game)
           (format-seats (:game/seats game))
           (card/format-line-short (:game.kitty/cards game))
           (format-bids (:game/bids game))
           (format-kitty-exchanged? game)
           (format-tricks game))))
