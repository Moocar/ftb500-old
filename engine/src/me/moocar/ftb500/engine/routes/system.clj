(ns me.moocar.ftb500.engine.routes.system
  (:require [com.stuartsierra.component :as component]
            me.moocar.ftb500.engine.routes.add-game
            me.moocar.ftb500.engine.routes.login))

(defn new-system [config]
  (component/system-using
   (component/system-map
    :routes/add-game (me.moocar.ftb500.engine.routes.add-game/map->AddGame {})
    :routes/login (me.moocar.ftb500.engine.routes.login/map->Login {})
    :routes/logout (me.moocar.ftb500.engine.routes.login/map->Logout {}))
   {:routes/add-game [:datomic]
    :routes/login [:datomic :user-store]
    :routes/logout [:datomic :user-store]}))
