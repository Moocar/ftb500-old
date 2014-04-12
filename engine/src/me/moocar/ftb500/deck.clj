(ns me.moocar.ftb500.deck
  (:require [clojure.core.async :refer [>! <! put!]]
            [clojure.set :as set]))

(def suits
  (array-map :spades {:color :black}
             :clubs {:color :black}
             :diamonds {:color :red}
             :hearts {:color :red}))

(defn suit>
  [s1 s2]
  (> (.indexOf (keys suits) s1)
     (.indexOf (keys suits) s2)))

(def suit-rank-set
  #{{:name "4"}
    {:name "5"}
    {:name "6"}
    {:name "7"}
    {:name "8"}
    {:name "9"}
    {:name "10"}
    {:name "Jack"}
    {:name "Queen"}
    {:name "King"}
    {:name "Ace"}})

(def no-trump-rank-order
  [{:name "4"}
   {:name "5"}
   {:name "6"}
   {:name "7"}
   {:name "8"}
   {:name "9"}
   {:name "10"}
   {:name "Jack"}
   {:name "Queen"}
   {:name "King"}
   {:name "Ace"}])

(def joker
  {:name "Joker"})

(defprotocol BidStyle
  (get-suit [this card])
  (valid-play? [this leading-card hand card])
  (winning-card [this trick-result]))

(defn trump-rank-order
  [trump-suit]
  (let [trump-color (:color (get suits trump-suit))
        trump-side-suit (->> suits
                             (filter #(= trump-color (:color %)))
                             (remove #(= trump-suit %))
                             (first))]
    (concat (map #(hash-map :name % :suit trump-suit)
                 (concat (range 4 11) ["Queen" "King" "Ace"]))
            {:name "Jack" :suit trump-side-suit}
            {:name "Jack" :suit trump-suit}
            {:name "Joker"})))

(defrecord SuitStyle [trump-suit trump-rank-order]
  BidStyle
  (get-suit [this card]
    (if (or (= trump-suit (:suit card))
            (and (= "Jack" (:name (:rank card)))
                 (= (:color (get suits (:suit card)))
                    (:color (get suits trump-suit))))
            (= "Joker" (:name (:rank card))))
      trump-suit
      (:suit card)))
  (valid-play? [this leading-card hand card]
    (assert (map? leading-card))
    (assert (set? hand))
    (assert (map? card))
    (let [actual-leading-suit (get-suit this leading-card)
          actual-card-suit (get-suit this card)]
      (if (= actual-leading-suit actual-card-suit)
        true
        (if (= actual-leading-suit trump-suit)
          (empty? (set/intersection (set hand)
                                    (set trump-rank-order)))
          (empty? (filter #(= actual-leading-suit (:suit %)) hand))))))
  (winning-card [this trick-result]
    (let [actual-leading-suit (get-suit this (:card (first trick-result)))]
      (reduce (fn [card1 card2]
                (let [card1-suit (get-suit this card1)
                      card2-suit (get-suit this card2)]
                  (if (not= card1-suit card2-suit)
                    (if (= card2-suit trump-suit)
                      card2
                      card1)
                    (if (= trump-suit card1-suit)
                      (if (> (.indexOf trump-rank-order card1)
                             (.indexOf trump-rank-order card2))
                        card1
                        card2)
                      (if (> (.indexOf no-trump-rank-order card1)
                             (.indexOf no-trump-rank-order card2))
                        card1
                        card2)))))))))

(defn new-suit-style
  [trump-suit]
  (->SuitStyle trump-suit (trump-rank-order trump-suit)))

(defn new-bid-style
  [bid]
  (when (contains? bid :suit)
    (new-suit-style (:suit bid))))

(defn no-trump-rank-order
  []
  (map #(hash-map :name %)
       (concat (range 4 11) ["Jack" "Queen" "King" "Ace" "Joker"])))

(defn make-red-suit-deck
  []
  (for [suit [:hearts :diamonds]
        rank suit-rank-set]
    (cond-> {:rank rank}
            suit (assoc :suit suit))))

(defn make-black-suit-deck
  []
  (for [suit [:clubs :spades]
        rank suit-rank-set
        :when (not= (:name rank) "4")]
    (cond-> {:rank rank}
            suit (assoc :suit suit))))

(defn make-deck
  []
  (->> (concat (make-red-suit-deck)
               (make-black-suit-deck))
       (cons {:rank joker})
       (shuffle)))

(defn partition-hands
  [deck]
  (let [partitions (partition-all 10 deck)]
    {:hands (take 4 partitions)
     :kitty (last partitions)}))
