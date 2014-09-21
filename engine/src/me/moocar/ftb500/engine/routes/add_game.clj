(ns me.moocar.ftb500.engine.routes.add-game
  (:require [datomic.api :as d]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]))

(defn- new-seat-tx [game-db-id position]
  (let [seat-db-id (d/tempid :db.part/user)
        seat-id (d/squuid)]
    [[:db/add seat-db-id :seat/id seat-id]
     [:db/add seat-db-id :seat/position position]
     [:db/add game-db-id :game/seats seat-id]]))

(defn- new-game-tx [game-id deck]
  {:pre [game-id (coll? deck)]}
  (let [game-db-id (d/tempid :db.part/user)]
    (concat
     (mapcat #(new-seat-tx game-db-id %) (range 4))
     [[:db/add game-db-id :game/id game-id]
      [:db/add game-db-id :game/deck (:db/id deck)]])))

(defn check-logged-in
  [request]
  (when-not (:logged-in-user-id request)
    (when-let [callback (:callback request)]
      :must-be-logged-in)))

(defn check-num-players
  [request]
  (let [{:keys [body callback]} request
        {:keys [num-players]} body]
    (cond (not num-players) :num-players-required
          (not (number? num-players)) :num-players-must-be-number)))

(defrecord AddGame [datomic]
  routes/Route
  (serve [this db request]
    (let [{:keys [body callback]} request
          {:keys [num-players]} body
          response (or ((some-fn check-logged-in check-num-players) request)
                       (let [deck (card/find-deck db num-players)
                             game-id (d/squuid)
                             tx (new-game-tx game-id deck)]
                         @(datomic/transact-action this tx game-id :action/create-game)
                         [:success {:game/id game-id}]))]
      (callback response))))
