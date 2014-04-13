(ns me.moocar.ftb500.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.players :as players]))

(defn new-system
  []
  (component/system-map
   :db (db/new-datomic-database)))
