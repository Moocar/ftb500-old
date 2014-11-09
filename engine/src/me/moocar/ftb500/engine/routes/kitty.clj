(ns me.moocar.ftb500.engine.routes.kitty
  (:require [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.routes.game-info :as game-info]
            [me.moocar.ftb500.engine.transport :as transport]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]
            [me.moocar.ftb500.engine.tx-listener :as tx-listener]
            [me.moocar.ftb500.schema :as schema :refer [uuid? card?]]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.seats :as seats]))

(defn load-cards [db cards]
  (map #(card/find db %) cards))

(defn implementation [this db request]
  (let [{:keys [datomic log]} this
        {:keys [logged-in-user-id body callback]} request
        {cards :cards seat-id :seat/id} body]
    (log/log log "handling kitty request")
    (callback
     (cond 
      
      ;; Check basic inputs

      (not logged-in-user-id) :must-be-logged-in
      (not seat-id) :seat-id-required
      (not (uuid? seat-id)) :seat-id-must-be-uuid
      (not (= 3 (count cards))) :three-cards-required

      :else ;; Load entities
      
      (let [seat (datomic/find db :seat/id seat-id)
            _         (log/log log "loading cards")
            cards (load-cards db cards)]
        (log/log log {:loaded [(:card/rank (first cards))
                               (:card/suit (first cards))
                               (:card.rank/name (:card/rank (first cards)))
                               (select-keys (first cards) [:card/suit :card/rank])]})
        (log/log log "after")
        
        (cond 
         
         (not (= 3 (count cards))) :failed-to-load-3-cards
         (not (every? card? cards)) :failed-to-load-cards
         
         :main
         
         (let [a 1]
           (log/log log {:exchanging cards})
           [:success])))))))

(defrecord ExchangeKitty [datomic log]
  routes/Route
  (serve [this db request]
    (implementation this db request)))
