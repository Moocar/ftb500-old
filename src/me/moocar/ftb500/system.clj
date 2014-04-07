(ns me.moocar.ftb500.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.game2 :as game2]))

(defn new-system
  []
  (component/system-map
   :db (db/new-datomic-database)
   :games (game2/new-games)))
