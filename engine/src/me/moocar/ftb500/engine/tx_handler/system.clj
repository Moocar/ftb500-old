(ns me.moocar.ftb500.engine.tx-handler.system
  (:require [com.stuartsierra.component :as component]
            me.moocar.ftb500.engine.routes.add-game
            me.moocar.ftb500.engine.routes.join-game))

(defn new-system [config]
  (component/system-using
   (component/system-map
    :tx-handler/create-game (me.moocar.ftb500.engine.routes.add-game/map->AddGameTxHandler {})
    :tx-handler/join-game   (me.moocar.ftb500.engine.routes.join-game/map->JoinGameTxHandler {})
    :tx-handlers {})
   {:tx-handler/create-game [:engine-transport]
    :tx-handler/join-game   [:engine-transport]
    :tx-handlers [:tx-handler/create-game
                  :tx-handler/join-game]}))
