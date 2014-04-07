(ns me.moocar.ftb500.card
  (:require [clojure.string :as string]))

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
  [card]
  (let [rank-name (name (:card/rank card))
        suit (:card/suit card)]
    (str (get short-rank-strings
              rank-name)
         (when suit
           (get short-suit-strings
                (name (:card.suit/name suit)))))))

(defn format-line-short
  [cards]
  (string/join " " (map format-short cards)))
