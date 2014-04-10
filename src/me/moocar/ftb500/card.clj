(ns me.moocar.ftb500.card
  (:require [clojure.string :as string]))

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
