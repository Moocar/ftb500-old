(ns me.moocar.ftb500.card
  (:require [clojure.string :as string]
            [datomic.api :as d])
  (:refer-clojure :exclude [find]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Formatting

(def short-suit-strings
  {"spades"   \u2660
   "clubs"    \u2663
   "diamonds" \u2666
   "hearts"   \u2665})

(def short-rank-strings
  {"four"  \4
   "five"  \5
   "six"   \6
   "seven" \7
   "eight" \8
   "nine"  \9
   "ten"   "10"
   "jack"  \J
   "queen" \Q
   "king"  \K
   "ace"   \A
   "joker" \B})

(defn format-short
  "Formats a card as a short string. E.g 10C or 2H"
  [card]
  (let [rank-name (name (:card.rank/name (:card/rank card)))
        suit (:card/suit card)]
    (str (format "%2s"(get short-rank-strings rank-name))
         (if suit
           (get short-suit-strings
                (name (:card.suit/name suit)))
           " "))))

(defn format-line-short
  [cards]
  (string/join " " (map format-short cards)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## API

(defn find
  [db {:keys [suit rank]}]
  (let [full-suit (keyword "card.suit.name" (name suit))
        full-rank (keyword "card.rank.name" (name rank))]
   (-> '[:find ?card
         :in $ ?suit-name ?rank-name
         :where [?suit :card.suit/name ?suit-name]
                [?rank :card.rank/name ?rank-name]
                [?card :card/suit ?suit]
                [?card :card/rank ?rank]]
       (d/q db full-suit full-rank)
       (ffirst)
       (->> (d/entity db)))))
