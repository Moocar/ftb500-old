(ns me.moocar.ftb500.engine.routes.game-info
  (:require [datomic.api :as d]
            [me.moocar.lang :refer [uuid?]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.datomic.schema :as db-schema]
            [me.moocar.ftb500.engine.routes :as routes])
  (:refer-clojure :exclude [find]))

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
        (let [game-ent-id (datomic/find-entity-id db :game/id game-id)]
          [:success (db-schema/pull-game db game-ent-id)]))))))
