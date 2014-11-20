(ns me.moocar.ftb500.engine.routes.game-info
  (:require [datomic.api :as d]
            [me.moocar.lang :refer [uuid?]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.datomic.schema :as db-schema]
            [me.moocar.ftb500.engine.routes :as routes])
  (:refer-clojure :exclude [find]))

(defn find
  [db game-id]
  (datomic/find db :game/id game-id))

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
        (let [game (d/touch (find db game-id))]
          [:success (db-schema/game-ext-form game)]))))))
