(ns me.moocar.ftb500.trick
  (:require [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]
            [me.moocar.ftb500.schema
             :refer [trick-game? player-bid? card? play? trick? seat? game? bid?]]
            [me.moocar.ftb500.protocols :as protocols]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Trumps Contract

(defn trump?
  "Returns true if card is a trump. Including jokers and left/right bowers"
  [trumps-contract card]
  {:pre [(card? card)]}
  (contains? (set (:trump-order trumps-contract)) card))

(defn relevant-play?
  "Returns true if the play follows suit, or is a trump"
  [trumps-contract leading-suit play]
  (let [{:keys [trick.play/card]} play]
    (or (= (:card/suit card) leading-suit)
        (trump? trumps-contract card))))

(defn play>
  "Given 2 plays, returns the play that played the winning card"
  [trumps-contract play1 play2]
  (if (protocols/card> trumps-contract
                       (:trick.play/card play1)
                       (:trick.play/card play2))
    play1
    play2))

(defn find-leading-suit
  [trick]
  {:pre [(trick? trick)]}
  (get-in (first (:trick/plays trick))
          [:trick.play/card :card/suit]))

(defrecord TrumpsContract [trump-suit trump-order]
  protocols/ContractStyle
  (card> [this card1 card2]
    {:pre [(card? card1) (card? card2)]}
    (let [card1-trump? (trump? this card1)
          card2-trump? (trump? this card2)]
      (or (and card1-trump? (not card2-trump?))
          (if (and card1-trump? card2-trump?)
            (do (> (.indexOf trump-order card1)
                   (.indexOf trump-order card2)))
            (if (and (not card1-trump?) (not card2-trump?))
              (> (:card.rank/no-trumps-order (:card/rank card1))
                 (:card.rank/no-trumps-order (:card/rank card2)))
              false)))))
  (-trick-winner [this plays]
    {:pre [(every? play? plays)]}
    (let [leading-play (first plays)
          leading-suit (find-leading-suit plays)
          plays-that-matter (->> (rest plays)
                                 (filter #(relevant-play? this leading-suit %))
                                 (cons leading-play))]
      (reduce (partial play> this)
              plays-that-matter))))

(defn find-trump-left-suit
  "Returns the left trump suit. E.g if trumps are hearts, the left
  trump suit it diamonds"
  [deck trump-suit]
  {:pre [deck trump-suit]
   :post [(card? %)]}
  (let [cards (:deck/cards deck)
        trump-color (:card.suit/color trump-suit)]
    (first
     (filter (fn [{:keys [card/suit] :as card}]
               (and (= (:card.suit/color suit) trump-color)
                    (not= suit trump-suit)))
             cards))))

(defn jack?
  [card]
  (= :card.rank.name/jack
     (:card.rank/name (:card/rank card))))

(defn trump-order
  "Returns a sorted list of cards in trump order, which is 4-10, Q, K,
  A, Left Bower, Right Bower, Joker "
  [deck trump-suit]
  (let [deck-cards (:deck/cards deck)
        trump-left-suit (find-trump-left-suit deck trump-suit)
        trump-left-cards (filter #(= trump-left-suit (:card/suit %)) deck-cards)
        raw-trump-cards (->> deck-cards
                             (filter #(= trump-suit (:card/suit %)))
                             (sort-by (comp :card.rank/no-trumps-order :card/rank)))
        till-jack (take-while (complement jack?) raw-trump-cards)
        left-bower (first (filter jack? trump-left-cards))
        right-bower (first (filter jack? raw-trump-cards))
        rest-of-trumps (rest (drop-while (complement jack?) raw-trump-cards))
        joker (first (filter #(= :card.rank.name/joker (:card.rank/name (:card/rank %))) deck-cards))]
    (concat till-jack
            rest-of-trumps
            [left-bower right-bower]
            [joker])))

(defn new-trumps-contract
  [deck trump-suit]
  (->TrumpsContract trump-suit (trump-order deck trump-suit)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General Trick

(defn new-contract
  "Returns a new contract object for the game and winning-bid"
  [game winning-bid]
  {:pre [(game? game)
         (player-bid? winning-bid)]}
  (let [{:keys [player-bid/bid]} winning-bid
        {:keys [bid/contract-style]} bid]
    (assert (bid? bid))
    (assert contract-style)
    (if (= :bid.contract-style/trumps contract-style)
      (new-trumps-contract (:game/deck game) (:bid/suit bid))
      (throw (ex-info "Unsupported Contract"
                      {:contract-style contract-style})))))

(defn trick-winner
  "Returns the winning play for a trick. Returns nil if trick is not
  finished"
  [game trick]
  {:pre [(trick-game? game)
         (trick? trick)]}
  (protocols/-trick-winner (:contract-style game)
                           (:trick/plays trick)))

(defn finished?
  "Returns true if trick is finished. I.e all cards have been played"
  [game trick]
  {:pre [(trick-game? game)
         (trick? trick)]}
  (let [num-players (count (:game/seats game))]
    (= num-players (count trick))))

(defn next-seat
  "Returns the next seat expected to play a card"
  [game]
  {:pre [(trick-game? game)]}
  (let [{:keys [game/tricks game/seats]} game
        winning-bid (bid/winner game)]
    (if (empty? tricks)
      (:player-bid/seat (bid/winner game))
      (if (empty? (last tricks))
        (trick-winner game (last tricks))
        (seats/next seats (:trick.play/seat (last (:trick/plays (last tricks)))))))))

