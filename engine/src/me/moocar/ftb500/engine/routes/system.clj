(ns me.moocar.ftb500.engine.routes.system
  (:require [com.stuartsierra.component :as component]
            me.moocar.ftb500.engine.routes.add-game
            me.moocar.ftb500.engine.routes.game-info
            me.moocar.ftb500.engine.routes.login))

(defn new-system [config]
  (component/system-using
   (component/system-map
    :routes/add-game (me.moocar.ftb500.engine.routes.add-game/map->AddGame {})
    :routes/game-info (me.moocar.ftb500.engine.routes.game-info/map->GameInfo {})
    :routes/login (me.moocar.ftb500.engine.routes.login/map->Login {})
    :routes/logout (me.moocar.ftb500.engine.routes.login/map->Logout {})
    :routes/signup (me.moocar.ftb500.engine.routes.login/map->Signup {}))
   {:routes/add-game [:datomic :log]
    :routes/game-info [:datomic :log]
    :routes/login  [:user-store]
    :routes/logout [:user-store]
    :routes/signup [:datomic]}))
