(ns me.moocar.ftb500.engine.datomic.schema
  (:require [datomic.api :as d]
            [me.moocar.ftb500.schema :as schema]))

(def tempid #(d/tempid :db.part/user))

(defn ref-enum
  [ident]
  [:db/add (tempid) :db/ident ident])

(defn enums []
  (map ref-enum 
       (concat schema/suit-names
               schema/suit-colors
               schema/rank-names
               schema/bid-names
               schema/contract-styles)))

(defn non-4-player-deck-card? 
  [{:keys [card/suit card/rank] :as card}]
  (let [{rank-name :card.rank/name} rank
        {suit-color :card.suit/color} suit]
    (or (= :card.rank.name/two rank-name)
        (= :card.rank.name/three rank-name)
        (and (= :card.rank.name/four rank-name)
             (= :card.suit.color/black suit-color)))))

(defn update-bid [suits]
  (fn [schema-bid]
    (let [{:keys [bid/suit]} schema-bid
          found-suit (first (filter #(= suit (dissoc % :db/id)) suits))]
      (-> schema-bid
          (dissoc :bid/suit)
          (assoc :db/id (tempid))
          (cond-> found-suit (assoc :bid/suit (:db/id found-suit)))))))

(defn find-joker [ranks]
  (->> ranks
       (filter (comp schema/joker? 
                     #(dissoc % :db/id)))
       (first)))

(defn ref-data []
  (let [suits (map #(assoc % :db/id (tempid)) schema/suits)
        ranks (map #(assoc % :db/id (tempid)) schema/ranks)
        cards (conj
               (for [suit suits
                     rank (remove schema/joker-rank? ranks)]
                 {:db/id (tempid)
                  :card/suit (:db/id suit)
                  :card/rank (:db/id rank)})
               {:db/id (tempid)
                :card/rank (:db/id (find-joker ranks))})
        deck {:db/id (tempid)
              :deck/num-players 4}
        deck-4-players-cards (map #(hash-map :db/id (:db/id deck)
                                             :deck/cards (:db/id %))
                                  (remove non-4-player-deck-card? cards))
        bids (map (update-bid suits) schema/bids)]
    (concat suits
            ranks
            cards
            [deck]
            deck-4-players-cards
            bids)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Touching

(defn sort-plays [plays]
  (sort-by :db/id plays))

(defn touch-trick [trick]
  (-> (into {} trick)
      (update-in [:trick/plays] sort-plays)
      (assoc :db/id (:db/id trick))))

(defn sort-tricks [tricks]
  (->> tricks
       (map touch-trick)
       (sort-by :db/id)))

(defn touch-game
  [{:keys [game/tricks game/bids game/seats db/id] :as game}]
  (-> (into {} game)
      (cond-> tricks (update-in [:game/tricks] sort-tricks)
              bids   (update-in [:game/bids] #(sort-by :db/id %))
              seats  (update-in [:game/seats] #(sort-by :seat/position %)))
      (assoc :db/id id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## External form

(def card-ext-pattern
  '[{:card/suit [{:card.suit/name [:db/ident]}]
     :card/rank [{:card.rank/name [:db/ident]}]}])

(def seat-ext-pattern
  '[:seat/id
    :seat/position
    {:seat/player [:user/id :player/name]}
    :seat/team])

(def game-ext-pattern
  [:game/id
   {:game/deck [{:deck/cards card-ext-pattern}
                :deck/num-players]}
   {:game/seats seat-ext-pattern}
   {:game/first-seat seat-ext-pattern}])

(def player-bid-ext-pattern
  [{:player-bid/seat [:seat/id]}
   {:player-bid/bid [{:bid/name [:db/ident]}]}])

(defn suit-ext-form
  [suit]
  suit)

(defn card-ext-form
  [card]
  (-> card
      (select-keys [:card/suit :card/rank])
      (update-in [:card/suit] select-keys [:card.suit/name])
      (update-in [:card/rank] select-keys [:card.rank/name])))

(defn bid-ext-form [bid]
  (select-keys bid [:bid/name]))

(defn deck-ext-form [deck]
  (-> deck
      (->> (into {}))
      (update-in [:deck/cards] #(map card-ext-form %))))

(defn seat-ext-form [seat]
  (-> seat
      d/touch
      (select-keys [:seat/id :seat/position :seat/player :seat/cards :seat/team])
      (dissoc :seat/cards)
      (cond-> (contains? seat :seat/player)
              (update-in [:seat/player] select-keys [:user/id :player/name]))))

(defn game-ext-form
  [game]
  (-> game
      (select-keys [:game/id :game/deck :game/seats :game/first-seat])
      (update-in [:game/deck] deck-ext-form)
      (update-in [:game/seats] #(map seat-ext-form %))
      (cond-> (contains? game :game/first-seat)
              (update-in [:game/first-seat] select-keys [:seat/id]))
      (assoc :game/bids [])))

