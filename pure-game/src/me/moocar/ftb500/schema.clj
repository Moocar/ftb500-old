(ns me.moocar.ftb500.schema
  (:require [me.moocar.ftb500.protocols :as protocols])
  (:import (java.util UUID)))

(def joker-rank
  {:card.rank/name :card.rank.name/joker
   :card.rank/no-trumps-order 13})

(def jack
  {:card.rank/name :card.rank.name/jack
   :card.rank/no-trumps-order 9})

(def ranks
  #{{:card.rank/name :card.rank.name/two
     :card.rank/no-trumps-order 0}
    {:card.rank/name :card.rank.name/three
     :card.rank/no-trumps-order 1}
    {:card.rank/name :card.rank.name/four
     :card.rank/no-trumps-order 2}
    {:card.rank/name :card.rank.name/five
     :card.rank/no-trumps-order 3}
    {:card.rank/name :card.rank.name/six
     :card.rank/no-trumps-order 4}
    {:card.rank/name :card.rank.name/seven
     :card.rank/no-trumps-order 5}
    {:card.rank/name :card.rank.name/eight
     :card.rank/no-trumps-order 6}
    {:card.rank/name :card.rank.name/nine
     :card.rank/no-trumps-order 7}
    {:card.rank/name :card.rank.name/ten
     :card.rank/no-trumps-order 8}
    jack
    {:card.rank/name :card.rank.name/queen
     :card.rank/no-trumps-order 10}
    {:card.rank/name :card.rank.name/king
     :card.rank/no-trumps-order 11}
    {:card.rank/name :card.rank.name/ace
     :card.rank/no-trumps-order 12}
    joker-rank})

(def rank-names
  (map :card.rank/name ranks))

(defn joker? [card]
  (= card joker-rank))

(defn jack? [card]
  (= (select-keys (:card/rank card) (keys jack))
     jack))

(defn joker-rank? [rank]
  (= (:card.rank/name rank)
     (:card.rank/name joker-rank)))

(def suit-colors
  #{:card.suit.color/red
    :card.suit.color/black})

(def suits
  [{:card.suit/name :card.suit.name/spades
    :card.suit/color :card.suit.color/black}
   {:card.suit/name :card.suit.name/clubs
    :card.suit/color :card.suit.color/black}
   {:card.suit/name :card.suit.name/diamonds
    :card.suit/color :card.suit.color/red}
   {:card.suit/name :card.suit.name/hearts
    :card.suit/color :card.suit.color/red}])

(def suit-names
  (set (map :card.suit/name suits)))

(def cards
  (set
   (conj
    (for [suit suits
          rank (remove joker? ranks)]
      {:card/suit suit
       :card/rank rank})
    {:card/rank joker-rank})))

(def contract-styles
  #{:bid.contract-style/trumps
    :bid.contract-style/no-trumps
    :bid.contract-style/misere
    :bid.contract-style/open-misere})

(def bid-tricks
  [{:bid/tricks 6
    :kw :six}
   {:bid/tricks 7
    :kw :seven}
   {:bid/tricks 8
    :kw :eight}
   {:bid/tricks 9
    :kw :nine}
   {:bid/tricks 10
    :kw :ten}])

(def trump-bids
  (for [bid-trick bid-tricks
        suit suits]
    (let [suit-name (name (:card.suit/name suit))]
      {:bid/name (keyword "bid.name" (str (name (:kw bid-trick)) \- suit-name))
       :bid/contract-style :bid.contract-style/trumps
       :bid/suit suit
       :bid/tricks (:bid/tricks bid-trick)})))

(def no-trump-bids
  (for [bid-trick bid-tricks]
    {:bid/name (keyword "bid.name" (str (name (:kw bid-trick)) "-no-trumps"))
     :bid/contract-style :bid.contract-style/no-trumps
     :bid/tricks (:bid/tricks bid-trick)}))

(def miseres
  [{:bid/name :bid.name/misere
    :bid/score 250
    :bid/contract-style :bid.contract-style/misere}
   {:bid/name :bid.name/open-misere
    :bid/score 500
    :bid/contract-style :bid.contract-style/open-misere}])

(def bids
  (let [trumps-no-trumps (flatten
                          (interleave (partition 4 trump-bids)
                                      no-trump-bids))]
    (concat
     (map (fn [bid score]
            (assoc bid :bid/score score))
          trumps-no-trumps
          (range 40 520 20))
     miseres)))

(def bid-names
  (set (map :bid/name bids)))

(def bid-scores
  (map :bid/score bids))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Fns

(defn touch-card
  [card]
  (first (filter #(and (= (:card.suit/name (:card/suit card))
                          (:card.suit/name (:card/suit %)))
                       (= (:card.rank/name (:card/rank card))
                          (:card.rank/name (:card/rank %))))
                 cards)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Checkers

(def uuid?
  #(instance? UUID %))

(defmacro check
  [thing conditions]
  `(do ~@(map (fn [condition]
                `(when-not (~condition ~thing)
                   (throw (ex-info "Validation failed"
                                   {:condition ~(list 'quote condition)
                                    :object ~thing}))))
              conditions)
       true))

(defmacro check-map
  [thing kvs]
  `(do (check ~thing [associative?])
       ~@(map (fn [[k v]]
                `(do 
                   (when-not (contains? ~thing ~k)
                     (throw (ex-info "Validation failed"
                                     {:key ~(list 'quote k)
                                      :not :present})))
                   (when-not (~v (~k ~thing))
                     (throw (ex-info "Validation failed"
                                     {:key ~(list 'quote k)
                                      :expected ~(list 'quote v)
                                      :actual (~k ~thing)})))
                   ))
              kvs)
       true))

(defmacro map-checker [checks]
  `(fn [thing#]
     (check-map thing# ~checks)))

(defn suit? [suit]
  (check suit [(comp (set suits)
                     #(select-keys % [:card.suit/name :card.suit/color]))]))

(defn rank? [rank]
  (check rank [(comp (set ranks)
                     #(select-keys % [:card.rank/name :card.rank/no-trumps-order]))]))

(defn card? [card]
  (when (:card/suit card)
    (check card [(comp suit? :card/suit)]))
  (check card [(comp rank? :card/rank)]))

(defn ext-card? [card]
  (let [{:keys [card/suit card/rank]} card]
    (check card [(comp (set rank-names) :card.rank/name :card/rank)
                 (fn [card]
                   (if (joker-rank? rank)
                     true
                     ((set suit-names) (:card.suit/name (:card/suit card)))))])))

(def deck?
  (map-checker {:deck/num-players number?
                :deck/cards #(every? card? %)}))

(def player?
  (map-checker {:user/id uuid?}))

(defn seat? [seat]
  (when (:seat/player seat)
    (check-map seat {:seat/player player?}))
  (check-map seat {:seat/id uuid?
                   :seat/position number?}))


(defn bid-trick? [bid-trick]
  (check bid-trick [#{6 7 8 9 10}]))

(defn bid-name? [bid-name]
  (check bid-name [(set bid-names)]))

(defn contract-style? [style]
  (check style []))

(defn bid? [bid]
  (when (:bid/tricks bid)
    (check-map bid {:bid/tricks #{6 7 8 9 10}}))
  (when (:bid/suit bid)
    (check-map bid {:bid/suit suit?}))
  (check-map bid {:bid/name (set bid-names)
                  :bid/contract-style (set contract-styles)
                  :bid/score (set bid-scores)}))

(defn player-bid? [player-bid]
  (when (contains? player-bid :player-bid/bid)
    (check-map player-bid {:player-bid/bid bid?}))
  (check-map player-bid {:player-bid/seat seat?}))

(def play?
  (map-checker {:trick.play/seat seat?
                :trick.play/card card?}))

(defn trick? [trick]
  (check-map trick {:trick/plays #(every? play? %)}))

(defn game? [game]
  (when (:game/bids game) 
    (check-map game {:game/bids #(every? player-bid? %)}))
  (check-map game {:game/id uuid?
                   :game/seats (every-pred #(every? seat? %) coll?)
                   :game/deck deck?}))

(defn trick-game? [game]
  (game? game)
  (when (:game/tricks game)
    (check-map game {:game/tricks #(every? trick? %)}))
  (check game [(comp not-empty :game/bids)
               (comp #(every? player-bid? %) :game/bids)
               #(contains? % :contract-style)]))


