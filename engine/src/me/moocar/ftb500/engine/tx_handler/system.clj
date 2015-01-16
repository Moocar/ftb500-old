(ns me.moocar.ftb500.engine.tx-handler.system
  (:require [com.stuartsierra.component :as component]
            me.moocar.ftb500.engine.routes.add-game
            me.moocar.ftb500.engine.routes.bids
            me.moocar.ftb500.engine.routes.join-game
            me.moocar.ftb500.engine.routes.kitty
            me.moocar.ftb500.engine.routes.play-card))

(defn new-system [config]
  (component/system-using
   (component/system-map
    :tx-handler/create-game    (me.moocar.ftb500.engine.routes.add-game/map->AddGameTxHandler {})
    :tx-handler/join-game      (me.moocar.ftb500.engine.routes.join-game/map->JoinGameTxHandler {})
    :tx-handler/deal-cards     (me.moocar.ftb500.engine.routes.join-game/map->DealCardsTxHandler {})
    :tx-handler/bid            (me.moocar.ftb500.engine.routes.bids/map->BidTxHandler {})
    :tx-handler/exchange-kitty (me.moocar.ftb500.engine.routes.kitty/map->ExchangeKittyTxHandler {})
    :tx-handler/play-card      (me.moocar.ftb500.engine.routes.play-card/map->PlayCardTxHandler {})
    :tx-handlers {})
   {:tx-handler/create-game    [:user-store]
    :tx-handler/join-game      [:user-store]
    :tx-handler/deal-cards     [:user-store]
    :tx-handler/bid            [:user-store :datomic]
    :tx-handler/exchange-kitty [:user-store]
    :tx-handler/play-card      [:user-store]
    :tx-handlers [:tx-handler/create-game
                  :tx-handler/join-game
                  :tx-handler/deal-cards
                  :tx-handler/bid
                  :tx-handler/exchange-kitty
                  :tx-handler/play-card]}))
