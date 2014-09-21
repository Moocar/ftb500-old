(ns me.moocar.ftb500.engine.routes.join-game
  (:require [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.game :as game]))

(defn uuid? [thing]
  (instance? java.util.UUID thing))

(defrecord JoinGame [datomic log]
  routes/Route
  (serve [this db request]
    (let [{:keys [logged-in-user-id body callback]} request
          {game-id :game/id seat-id :seat/id} body]
      (callback
       (cond (not logged-in-user-id) :must-be-logged-in
             (not game-id) :game-id-required
             (not seat-id) :seat-id-required
             (not (uuid? game-id)) :game-id-must-be-uuid
             (not (uuid? seat-id)) :seat-id-must-be-uuid

             :main
             (let [game (datomic/find db :game/id game-id)
                   seat (datomic/find db :seat/id seat-id)
                   player (datomic/find db :user/id logged-in-user-id)]
               (cond (not game) :game-does-not-exist
                     (not seat) :game-does-not-exist
                     (game/full? game) :game-is-already-full
                     (game/seat-taken? seat player) :seat-taken

                     :main
                     (let [tx [[:join-game (:db/id player) (:db/id game)]]]
                       @(datomic/transact-action datomic tx game-id :action/join-game)
                       [:success]))))))))
