(ns me.moocar.ftb500.handlers
  (:require [clojure.core.async :refer [put! go-loop <!]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.players :as players]))

(defn not-found-handler
  [_ client request]
  {:status :no-route
   :body {:msg "No Service Found"}})

(defn make-handler-lookup
  []
  {:create-player  [:players players/add!]
   :create-game    [:games   game/add!]
   :join-game      [:games   game/join!]
   :bid            [:games   game/bid!]
   :exchange-kitty [:games   game/exchange-kitty!]
   :play-card      [:games   game/play-card!]
   :game-view      [:games   game/view]})

(defn handle-request
  [component client request]
  (let [conn (:conn (:datomic component))
        db (d/db conn)
        handler-lookup (:handler-lookup component)
        {:keys [action args]} request
        {:keys [player-id game-id]} args
        player (when player-id (players/find db player-id))
        game (when game-id (game/find db game-id))]
    (cond (and player-id (not player))
          {:status :bad-args
           :body {:msg "Player not found"
                  :data {:player-id player-id}}}

          (and game-id (not game))
          {:status :bad-args
           :body {:msg "Game not found"
                  :data {:game-id game-id}}}

          :else
          (let [handler (get handler-lookup action [nil not-found-handler])
                [handler-component handler-fn] handler]
            (handler-fn (get component handler-component)
                        client
                        (assoc args
                          :player player
                          :game game))))))

(defn start-listen-loop
  [this]
  (go-loop []
    (try
      (when-let [request (<! (:request-ch this))]
        (let [[client payload response-ch] request
              {:keys [action args]} payload
              response (handle-request this client payload)]
          (put! response-ch response))
        (recur))
      (catch Throwable t
        (.printStackTrace t)))))

(defrecord HandlerComponent []
  component/Lifecycle
  (start [this]
    (start-listen-loop (assoc this :handler-lookup (make-handler-lookup)))
    (assoc this :handler-lookup (make-handler-lookup)))
  (stop [this]
    this))

(defn new-handler-component
  []
  (component/using (map->HandlerComponent {})
    [:datomic :games :players :clients :request-ch]))
