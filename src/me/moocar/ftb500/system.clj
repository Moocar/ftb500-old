(ns me.moocar.ftb500.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.players :as players]
            [me.moocar.ftb500.games :as games]))

(defn new-system
  []
  (component/system-map
   :db (db/new-datomic-database)
   :games (games/new-games)
   :players (players/new-players-component)))
