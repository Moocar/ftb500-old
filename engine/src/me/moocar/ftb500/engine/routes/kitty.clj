(ns me.moocar.ftb500.engine.routes.kitty
  (:require [datomic.api :as d]
            [me.moocar.async :as moo-async]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]
            [me.moocar.ftb500.schema :as schema :refer [uuid? card?]]))

(defn load-cards [db cards]
  (map #(card/find db %) cards))

(defn implementation [this db request]
  (let [{:keys [datomic log]} this
        {:keys [logged-in-user-id body]} request
        {cards :cards seat-id :seat/id} body]
    (cond
      
      ;; Check basic inputs

      (not logged-in-user-id) [:must-be-logged-in]
      (not seat-id) [:seat-id-required]
      (not (uuid? seat-id)) [:seat-id-must-be-uuid]
      (not (= 3 (count cards))) [:three-cards-required]

      :else ;; Load entities
      
      (let [seat (datomic/find db :seat/id seat-id)
            game (first (:game/_seats seat))
            cards (load-cards db cards)]

        (cond

          (not (= 3 (count cards))) [:failed-to-load-3-cards]
          (not (every? card? cards)) [:failed-to-load-cards]
          (not game) [:could-not-find-game]

          :main

          (let [game-id (:db/id game)
                current-kitty (:game.kitty/cards game)
                _ (assert (= 0 (count current-kitty)))
                retract-tx (map #(vector :db/retract (:db/id seat)
                                         :seat/cards (:db/id %))
                                cards)
                add-tx (map #(vector :db/add game-id
                                     :game.kitty/cards (:db/id %))
                            cards)
                tx (concat retract-tx add-tx)]
            @(datomic/transact-action datomic tx (:game/id game) :action/exchange-kitty)
            [:success]))))))

(defrecord ExchangeKitty [datomic log]
  routes/Route
  (serve [this db request]
    (implementation this db request)))

(defrecord ExchangeKittyTxHandler [user-store log]
  tx-handler/TxHandler
  (handle [this user-ids tx]
    (doseq [user-id user-ids]
      (let [msg {:route :exchange-kitty
                 :body {}}]
        (doseq [conn (user-store/user-conns user-store user-id)]
          (moo-async/send-off! (:send-ch conn) msg))))))
