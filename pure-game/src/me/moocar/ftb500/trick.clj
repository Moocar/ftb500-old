(ns me.moocar.ftb500.trick
  (:require [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]
            [me.moocar.ftb500.schema
             :refer [trick-game? player-bid? card? play? trick? seat? game? bid?
                     deck?]]
            [me.moocar.ftb500.protocols :as protocols]))

(defn play>
  "Given 2 plays, returns the play that played the winning card"
  [trumps-contract play1 play2]
  (if (protocols/card> trumps-contract
                       (:trick.play/card play1)
                       (:trick.play/card play2))
    play1
    play2))

(defn find-leading-suit
  "Finds the suit of the leading card in this trick"
  [trick]
  {:pre [(trick? trick)]}
  (get-in (first (:trick/plays trick))
          [:trick.play/card :card/suit]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## No Trumps

(defrecord NoTrumpsContract []
  protocols/ContractStyle
  (card> [this card1 card2]
    {:pre [(card? card1) (card? card2)]}
    (> (:card.rank/no-trumps-order (:card/rank card1))
       (:card.rank/no-trumps-order (:card/rank card2))))
  (follows-suit? [this suit play]
    (= (:card/suit (:trick.play/card play)) suit)))

(defn new-no-trumps-contract
  [deck trump-suit]
  (->NoTrumpsContract))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Trumps Contract

(defn trump?
  "Returns true if card is a trump. Including jokers and left/right bowers"
  [trumps-contract card]
  {:pre [(card? card)]}
  (contains? (set (:trump-order trumps-contract)) card))

(defn jack?
  [card]
  (= :card.rank.name/jack
     (:card.rank/name (:card/rank card))))

(defrecord TrumpsContract [trump-suit trump-order]
  protocols/ContractStyle
  (card> [this card1 card2]
    {:pre [(card? card1) (card? card2)]}
    (let [card1-trump? (trump? this card1)
          card2-trump? (trump? this card2)]
      (or (and card1-trump? (not card2-trump?))
          (if (and card1-trump? card2-trump?)
            (> (.indexOf trump-order card1)
               (.indexOf trump-order card2))
            (if (and (not card1-trump?) (not card2-trump?))
              (> (:card.rank/no-trumps-order (:card/rank card1))
                 (:card.rank/no-trumps-order (:card/rank card2)))
              false)))))
  (follows-suit? [this suit play]
    (let [{:keys [trick.play/card]} play]
      (or (= (:card/suit card) suit)
          (trump? this card)))))

;;; Trump order setup

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

(def new-trumps-contract
  (memoize
   (fn [deck trump-suit]
     (->TrumpsContract trump-suit (trump-order deck trump-suit)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; General Trick

(def new-contract
  "Returns a new contract object for the game and winning-bid"
  (memoize
   (fn [deck winning-bid]
     {:pre [(deck? deck)]}
     (let [{:keys [player-bid/bid]} winning-bid
           {:keys [bid/contract-style]} bid]
       (assert (bid? bid))
       (assert contract-style)

       (case contract-style

         :bid.contract-style/trumps
         (new-trumps-contract deck (:bid/suit bid))

         :bid.contract-style/no-trumps
         (new-no-trumps-contract)

         (throw (ex-info "Unsupported Contract"
                         {:contract-style contract-style})))))))

(defn update-contract
  [game]
  (assoc game :contract-style (new-contract (:game/deck game)
                                            (bid/winner game))))

(defn winner
  "Returns the winning play for a trick. Returns nil if trick is not
  finished"
  [{:keys [contract-style] :as game}
   {:keys [trick/plays] :as trick}]
  {:pre [(trick-game? game)
         (trick? trick)]}
  (let [leading-play (first plays)
        leading-suit (find-leading-suit trick)
        plays-that-matter (->> (rest plays)
                               (filter #(protocols/follows-suit? contract-style
                                                                 leading-suit
                                                                 %))
                               (cons leading-play))]
    (reduce (partial play> contract-style)
            plays-that-matter)))

(defn finished?
  "Returns true if trick is finished. I.e all cards have been played"
  [game trick]
  {:pre [(trick-game? game)
         (trick? trick)]}
  (let [num-players (count (:game/seats game))]
    (= num-players (count (:trick/plays trick)))))

(defn all-finished?
  "Returns true if all tricks are finished. I.e the game has ended"
  [{:keys [game/tricks] :as game}]
  (and (= 10 (count tricks))
       (every? #(finished? game %) tricks)))

(defn next-seat
  "Returns the next seat expected to play a card"
  [game]
  {:pre [(trick-game? game)]}
  (let [{:keys [game/tricks game/seats]} game
        winning-bid (bid/winner game)]
    (if (empty? tricks)
      (:player-bid/seat (bid/winner game))
      (if (empty? (last tricks))
        (winner game (last tricks))
        (seats/next seats (:trick.play/seat (last (:trick/plays (last tricks)))))))))
