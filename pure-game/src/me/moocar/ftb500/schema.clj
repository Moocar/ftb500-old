(ns me.moocar.ftb500.schema
  (:import (java.util UUID)))

(def joker-rank
  {:card.rank/name :card.rank.name/joker
   :card.rank/no-trumps-order 13})

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
    {:card.rank/name :card.rank.name/jack
     :card.rank/no-trumps-order 9}
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

(def suit?
  (map-checker {:card.suit/name (set suit-names)
                :card.suit/color (set suit-colors)}))

(defn card? [card]
  (check (dissoc card :db/id) [(set cards)]))

(def deck?
  (map-checker {:deck/num-players number?}))

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

(defn game? [game]
  (when (:game/bids game) 
    (check-map game {:game/bids #(every? player-bid? %)}))
  (check-map game {:game/id uuid?
                   :game/seats (every-pred #(every? seat? %) coll?)
                   :game/deck deck?}))


