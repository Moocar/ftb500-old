(ns me.moocar.ftb500.engine.routes.system
  (:require [com.stuartsierra.component :as component]
            me.moocar.ftb500.engine.routes.add-game
            me.moocar.ftb500.engine.routes.bids
            me.moocar.ftb500.engine.routes.game-info
            me.moocar.ftb500.engine.routes.join-game
            me.moocar.ftb500.engine.routes.kitty
            me.moocar.ftb500.engine.routes.login
            me.moocar.ftb500.engine.routes.play-card))

(defn new-system [config]
  (component/system-using
   (component/system-map
    :routes/add-game       (me.moocar.ftb500.engine.routes.add-game/map->AddGame {})
    :routes/bid            (me.moocar.ftb500.engine.routes.bids/map->Bid {})
    :routes/game-info      (me.moocar.ftb500.engine.routes.game-info/map->GameInfo {})
    :routes/join-game      (me.moocar.ftb500.engine.routes.join-game/map->JoinGame {})
    :routes/login          (me.moocar.ftb500.engine.routes.login/map->Login {})
    :routes/logout         (me.moocar.ftb500.engine.routes.login/map->Logout {})
    :routes/signup         (me.moocar.ftb500.engine.routes.login/map->Signup {})
    :routes/exchange-kitty (me.moocar.ftb500.engine.routes.kitty/map->ExchangeKitty {})
    :routes/play-card      (me.moocar.ftb500.engine.routes.play-card/map->PlayCard {}))
   {:routes/add-game       [:datomic :log]
    :routes/bid            [:datomic :log]
    :routes/game-info      [:datomic :log]
    :routes/join-game      [:datomic :log :tx-listener]
    :routes/login          [:user-store]
    :routes/logout         [:user-store]
    :routes/signup         [:datomic]
    :routes/exchange-kitty [:datomic :log]
    :routes/play-card      [:datomic :log]}))
