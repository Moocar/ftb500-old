(ns me.moocar.ftb500.handlers
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.players :as players]))

(defn not-found-handler
  [_ request]
  {:status 404
   :body {:msg "No Service Found"}})

(defn make-handler-lookup
  []
  {:create-player players/add!
   :create-game game/add!
   :join-game game/join!
   :bid game/bid!})

(defn handle-request
  [component request]
  (let [conn (:conn (:datomic component))
        db (d/db conn)
        handler-lookup (:handler-lookup component)
        {:keys [action args]} request
        {:keys [player-id game-id]} args
        player (when player-id (players/find db player-id))
        game (when game-id (game/find db game-id))]
    (cond (and player-id (not player))
          {:status 400
           :body {:msg "Player not found"
                  :data {:player-id player-id}}}

          (and game-id (not game))
          {:status 400
           :body {:msg "Game not found"
                  :data {:game-id game-id}}}

          :else
          (let [handler-fn (get handler-lookup action not-found-handler)]
            (handler-fn conn (assoc args
                               :player player
                               :game game))))))

(defrecord HandlerComponent [datomic]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler-lookup (make-handler-lookup)))
  (stop [this]
    this))

(defn new-handler-component
  []
  (component/using (map->HandlerComponent {})
    [:datomic]))
