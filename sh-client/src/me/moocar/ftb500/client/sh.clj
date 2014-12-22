(ns me.moocar.ftb500.client.sh
  (:require [com.stuartsierra.component :as component]
            [me.moocar.async :refer [<? go-try <!!?]]
            [me.moocar.ftb500.client :refer [game-send! send!]]
            [me.moocar.ftb500.schema :as schema
             :refer [game? seat? bid? player? uuid? ext-card? card?]]
            [me.moocar.lang :refer [uuid]]))

(defn prompt-name [{:keys [console]}]
  (let [name (.readLine console "Please enter your name: " (into-array Object []))]
    (.format console "Cheers %s\n" (into-array [name]))
    (.flush console)
    name))

(defn prompt-game-id [{:keys [console]}]
  (let [game-id-string (.readLine console "Which game would you like to join? " (into-array Object []))]
    (uuid game-id-string)))

(defn touch-game
  [game]
  (update-in game [:game/deck :deck/cards] #(map schema/touch-card %)))

(defn game-info
  [this game-id]
  (go-try (touch-game (second (<? (send! this :game-info {:game-id game-id}))))))

(defn ready-game
  "Initiates the ai map with the basic game information"
  [client game-id]
  {:pre [(:transport client)
         (uuid? game-id)]}
  (let [{:keys [route-pub-ch]} client]
    (go-try
     (-> client
         (assoc :game (<? (game-info client game-id)))
         (as-> client
             (assoc client :game/num-players (count (:game/seats (:game client)))))))))

(defrecord ShClient [transport]
  component/Lifecycle
  (start [this]
    (let [console (System/console)
          this (assoc this :console console)
          player-name (prompt-name this)
          game-id (prompt-game-id this)
          user-id (uuid)]
      (println "game id" game-id)
      (<!!? 
       (go-try
        (and (<? (send! this :signup {:user-id user-id}))
             (<? (send! this :login {:user-id user-id})))
        (let [game-info (<? (ready-game this game-id))]
          (println "game info")
          (clojure.pprint/pprint game-info))))))
  (stop [this]
    this))

(defn new-sh-client [config]
  (component/using (map->ShClient {})
    [:transport]))

