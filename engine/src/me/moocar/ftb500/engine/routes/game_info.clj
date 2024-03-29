(ns me.moocar.ftb500.engine.routes.game-info
  (:require [datomic.api :as d]
            [me.moocar.lang :refer [uuid?]]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.datomic.schema :as db-schema]
            [me.moocar.ftb500.engine.routes :as routes])
  (:refer-clojure :exclude [find]))

(defrecord GameInfo [datomic]
  routes/Route
  (serve [this db request]
    (let [{:keys [body]} request
          {:keys [game-id]} body]
      (cond
        (not game-id) [:game-id-required]
        (not (uuid? game-id)) [:game-id-must-be-uuid]

        :else
        (let [game-ent-id (datomic/find-entity-id db :game/id game-id)
              game (-> (datomic/pull db db-schema/game-ext-pattern game-ent-id)
                       (assoc :game/bids []))]
          [:success game])))))
