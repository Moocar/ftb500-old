(ns me.moocar.ftb500.engine.routes.game-info
  (:require [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes])
  (:refer-clojure :exclude [find]))

(defn uuid? [thing]
  (instance? java.util.UUID thing))

(defn check-game-id
  [request]
  (let [{:keys [body callback]} request
        {:keys [game-id]} body]
    (cond (not game-id) :game-id-required
          (not (uuid? game-id)) :game-id-must-be-uuid)))

(defn find
  [db game-id]
  (datomic/find db :game/id game-id))

(defn deck-ext-form [deck]
  (-> deck
      (select-keys [:deck/cards :deck/num-players])
      (update-in [:deck/cards] count)))

(defn seat-ext-form [seat]
  (-> seat
      d/touch
      (select-keys [:seat/id :seat/position :seat/player :seat/cards :seat/team])
      (update-in [:seat/cards] count)
      (update-in [:seat/player] :user/id)))

(defn ext-form
  [game]
  (-> game
      (select-keys [:game/id :game/deck :game/seats :game/first-seat])
      (update-in [:game/deck] deck-ext-form)
      (update-in [:game/seats] #(map seat-ext-form %))))

(defrecord GameInfo [datomic log]
  routes/Route
  (serve [this db request]
    (let [{:keys [body callback]} request
          {:keys [game-id]} body
          response (or (check-game-id request)
                       (let [game (d/touch (find db game-id))]
                         [:success (ext-form game)]))]
      (callback response))))
