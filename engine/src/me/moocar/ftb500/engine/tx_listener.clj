(ns me.moocar.ftb500.engine.tx-listener
  (:require [clojure.core.async :refer [put!]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.transport :as transport]))

(defn- log
  [this msg]
  (log/log (:log this) msg))

(defn- get-attr
  [tx attr-k]
  (-> '[:find ?eid
        :in $ ?attr-id
        :where [?eid ?attr-id]]
      (d/q (:tx-data tx) (:id (d/attribute (:db-after tx) attr-k)))
      ffirst
      (->> (d/entity (:db-after tx)))))

(defn- send!
  [this clients msg]
  (transport/send! (:transport this) clients msg))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers

(defmulti handle-tx-event
  (fn [this clients action-k tx]
    action-k))

(defmethod handle-tx-event :create-game
  [this clients _ tx]
  (send! this clients {:action :create-game}))

(defmethod handle-tx-event :join-game
  [this clients _ tx]
  (let [seat (get-attr tx :seat/player)
        player (:seat/player seat)
        msg {:action :join-game
             :seat/id (:seat/id seat)
             :player/id (:player/id player)}]
    (send! this clients msg)))

(defmethod handle-tx-event :deal-cards
  [this clients _ tx]
  (doseq [client clients]
    (let [db (:db-after tx)
          game (get-attr tx :game/first-seat)
          player (d/entity db (:db/id (:player client)))
          seat (players/get-seat player)
          msg {:action :deal-cards
               :cards (map cards/ext-form (:seat/cards seat))
               :game/first-seat (:game/first-seat game)}]
      (send! this [client] msg))))

(defn bid-winner
  [db winning-seat client]
  (->> (:player client)
       (:db/id)
       (d/entity db)
       (players/get-seat)
       (= winning-seat)))

(defmethod handle-tx-event :bid
  [this clients _ tx]
  (let [bid (get-attr tx :game.bid/bid)
        game (get-attr tx :game/bids)]
    (when (bids/finished? game)
      (let [bids (bids/get-bids game)
            winning-bid (bids/winning-bid bids)
            winning-seat (:game.bid/seat winning-bid)
            db (:db-after tx)]
        (when-let [connected-client (first (filter #(bid-winner db winning-seat %) clients))]
          (let [kitty-cards (:game.kitty/cards game)
                msg {:action :kitty
                     :kitty {:cards (map cards/ext-form kitty-cards)}}]
            (send! this [connected-client] msg)))))
    (let [msg {:action :bid
               :bid {:seat/id (:seat bid)
                     :bid/name (:bid/name bid)}}]
      (send! this clients msg))))

(defmethod handle-tx-event :exchange-kitty
  [this clients _ tx]
  (send! this clients {:action :exchange-kitty}))

(defmethod handle-tx-event :play-card
  [this clients _ tx]
  (let [play (get-attr tx :trick.play/card)
        seat (:trick.play/seat play)
        card (:trick.play/card play)
        msg {:action :play-card
             :play-card {:seat/id (:seat/id seat)
                         :card (cards/ext-form card)}}]
    (send! this clients msg)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handle Report Q

(defn get-game-id-and-actions
  [tx]
  (-> '[:find ?game-id ?action-k
        :in $ ?game-id-attr ?db-instant-attr ?action-attr
        :where [_ ?game-id-attr ?game-id ?tx]
               [?tx ?db-instant-attr]
               [?tx ?action-attr ?action-k]]
      (d/q (:tx-data tx)
           (:id (d/attribute (:db-after tx) :tx/game-id))
           (:id (d/attribute (:db-after tx) :db/txInstant))
           (:id (d/attribute (:db-after tx) :action)))
      (->> (map #(update-in % [1] (fn [i] (d/ident (:db-after tx) i)))))))

(defn find-connected-clients-for-game
  [this game-id]
  (get (:games @(:client-db this)) game-id))

(defn handle-tx
  [component tx]
  (let [{:keys [log]} component
        game-txs (get-game-id-and-actions tx)]
    (doseq [[game-id action-k] game-txs]
      (let [action-k (keyword (name action-k))
            clients (find-connected-clients-for-game component game-id)]
        ;; Utility ch to detect new games
        (when (= :create-game action-k)
          (when-let [new-games-ch (:new-games-ch component)]
            (put! new-games-ch game-id)))
        (when-not (empty? clients)
          (handle-tx-event component clients action-k tx))))))

(defn start-db-listener
  [component]
  (let [{:keys [log datomic]} component
        tx-report-queue (:tx-report-queue datomic)
        conn (:conn datomic)]
    (future
      (loop [tx (.take tx-report-queue)]
        (try
          (handle-tx component tx)
          (catch Throwable e
            (log/log log
                     {:msg "error in pubsub loop"
                      :ex e})))
        (recur (.take tx-report-queue))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Registering

(defn tx->datoms
  [conn tx-id]
  (d/q '[:find ?e ?a ?v ?tx ?added
         :in $ ?log ?tx
         :where [(tx-data ?log ?tx) [[?e ?a ?v _ ?added]]]
         #_[?a :db/ident ?aname]]
       (d/db conn)
       (d/log conn)
       tx-id))

(defn find-game-transactions
  [conn game-id]
  (-> '[:find ?tx
        :in $ ?log ?game-id
        :where [_ :tx/game-id ?game-id ?tx]]
      (d/q (d/db conn) (d/log conn) game-id)
      (->> (map #(tx->datoms conn (first %))))))

(defn register-client
  [this game-id client player]
  (let [{:keys [datomic]} this
        conn (:conn datomic)
        client (assoc client :player player)]
    (log this {:msg "register-client"
               :game-id game-id
               :player (:player/name player)})
    (swap! (:client-db this)
           update-in
           [:games game-id]
           (fn [clients]
             (if (not ((set clients) client))
               (conj clients client)
               clients)))
    (transport/send! transport [client] {:action :registered})
    (doseq [tx (find-game-transactions conn game-id)]
      (let [tx {:tx-data tx
                :db-after (d/db conn)}
            game-tx (first (get-game-id-and-actions tx))
            [game-id action-k] game-tx]
        (let [action-k (keyword (name action-k))
              clients (find-connected-clients-for-game this game-id)]
          (when-not (empty? clients)
            (handle-tx-event this clients action-k tx)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component

(defrecord Pubsub [datomic client-db log]
  component/Lifecycle
  (start [this]
    (start-db-listener this)
    this)
  (stop [this]
    (d/remove-tx-report-queue (:conn datomic))
    this))

(defn new-pubsub
  [config]
  (component/using (map->Pubsub {:client-db (atom {:games {}})})
    [:datomic :log :transport :new-games-ch]))
