(ns me.moocar.ftb500.engine.routes.game-info
  (:require [datomic.api :as d]
            [me.moocar.lang :refer [uuid?]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.datomic.schema :as db-schema]
            [me.moocar.ftb500.engine.routes :as routes])
  (:refer-clojure :exclude [find]))

(def seat-pattern
  [:seat/id
   :seat/position
   {:seat/player [:user/id :player/name]}
   :seat/team])

(def pull-pattern
  [:game/id
   {:game/deck [{:deck/cards [{:card/suit [{:card.suit/name [:db/ident]}]
                               :card/rank [{:card.rank/name [:db/ident]}]}]}
                :deck/num-players]}
   {:game/seats seat-pattern}
   {:game/first-seat seat-pattern}])

(defn dissoc-card-ident
  [card]
  (-> card
      (cond-> (contains? card :card/suit)
              (update-in [:card/suit :card.suit/name] :db/ident))
      (update-in [:card/rank :card.rank/name] :db/ident)))

(defn update-deck
  [game]
  (update-in game
             [:game/deck :deck/cards]
             #(map dissoc-card-ident %)))

(defrecord GameInfo [datomic log]
  routes/Route
  (serve [this db request]
    (let [{:keys [body callback]} request
          {:keys [game-id]} body]
      (callback
       (cond
        (not game-id) :game-id-required
        (not (uuid? game-id)) :game-id-must-be-uuid

        :else
        (let [game-ent-id (datomic/find-entity-id db :game/id game-id)
              ext-game (-> (d/pull db pull-pattern game-ent-id)
                           update-deck
                           (assoc :game/bids []))]
          [:success ext-game]))))))
