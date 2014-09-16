(ns me.moocar.ftb500.engine.routes.system
  (:require [com.stuartsierra.component :as component]
            me.moocar.ftb500.engine.routes.add-game))

(defn new-system [config]
  (component/system-using
   (component/system-map
    :routes/add-game (me.moocar.ftb500.engine.routes.add-game/map->AddGame {}))
   {:routes/add-game [:datomic]}))
