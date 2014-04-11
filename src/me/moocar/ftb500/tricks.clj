(ns me.moocar.ftb500.tricks
  (:require [datomic.api :as d]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.kitty :as kitty]))

(defn get-tricks
  [game]
  (sort-by :db/id (:game/tricks game)))

(defn finished?
  [trick]
  (let [game (first (:game/_tricks trick))
        seats (:game/seats game)]
    (= (count (:game.trick/plays trick))
       (count seats))))

(defn find-trump-left-suit
  [db trump-suit]
  {:pre [db trump-suit]}
  (let [trump-color (:card.suit/color trump-suit)]
    (-> '[:find ?suit
          :in $ ?trump-color ?trump-suit
          :where [?suit :card.suit/color ?trump-color]
          [(not= ?suit :card/suit ?trump-suit)]]
        (d/q db trump-color trump-suit)
        ffirst
        (->> (d/entity db)))))

(defn find-deck
  [db num-seats]
  (-> '[:find ?deck
        :in $ ?num-seats
        :where [?deck :deck/num-players ?num-seats]]
      (d/db db num-seats)
      ffirst
      (->> (d/entity db))))

(defn jack?
  [card]
  (= :card.rank.name/jack
     (:card.rank/name (:card/rank card))))

(defprotocol ContractStyle
  (card> [this card1 card2]))

(defn trump-card?
  [trumps-contract card]
  (contains? (set (:trump-order trumps-contract)) card))

(defn trump-order
  [db deck trump-suit]
  (let [deck-cards (:deck/cards deck)
        trump-left-suit (find-trump-left-suit db trump-suit)
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
            [left-bower right-bower]
            rest-of-trumps
            [joker])))

(defrecord TrumpsContract [trump-suit trump-order]
  ContractStyle
  (card> [this card1 card2]
    (let [card1-trump? (trump-card? this card1)
          card2-trump? (trump-card? this card2)]
     (or (and card1-trump? (not card2-trump?))
         (if (and card1-trump? card2-trump?)
           (> (.indexOf trump-order card1)
              (.indexOf trump-order card2))
           (if (and (not card1-trump?) (not card2-trump?))
             (> (:card.rank/no-trumps-order (:card/rank card1))
                (:card.rank/no-trumps-order (:card/rank card2)))
             false))))))

(defn new-trumps-contract
  [db deck trump-suit]
  (->TrumpsContract trump-suit (trump-order db deck trump-suit)))

(defn new-contract
  [db deck winning-bid]
  (let [bid (:game.bid/bid winning-bid)
        contract-style (:bid/contract-style bid)]
    (if (= :bid.contract-style/trumps contract-style)
      (new-trumps-contract db deck (:bid/suit bid))
      (throw (ex-info "Unsupported Contract" {:contract-style contract-style})))))

(defn calc-winner
  [contract trick]
  (reduce #(card> contract %1 %2) trick))

(defn add-play!
  [this seat card]
  (let [conn (:conn (:db this))
        db (d/db conn)
        game (first (:game/_seats seat))
        winning-bid (bids/winning-bid (:game/bids game))
        contract (new-contract db (:game/deck game) winning-bid)
        tricks (get-tricks game)
        last-trick (last tricks)]
    (if-not (kitty/exchanged? game)
      (throw (ex-info "Kitty not exchanged yet!"
                      {}))
      (if (empty? tricks)
        (if (not= seat (:game.bid/seat winning-bid))
          (throw (ex-info "Not your turn"
                          {}))
          (let [trick-id (d/tempid :db.part/user)
                trick-tx [{:db/id (:db/id game)
                           :game/tricks trick-id}]
                play-id (d/tempid :db.part/user)
                play-tx [{:db/id trick-id
                           :game.trick/plays play-id}
                         {:db/id play-id
                          :trick.play/seat (:db/id seat)
                          :trick.play/card (:db/id card)}]]
            @(d/transact conn (concat trick-tx play-tx))))
        (let [last-winner (calc-winner contract last-trick)]
          (println "last winner"))))))
