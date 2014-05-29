(ns me.moocar.ftb500.pubsub2
  (:require [clojure.core.async :refer [put!]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as cards]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.clients :as clients]))

(defn- get-attr
  [tx attr-k]
  (-> '[:find ?eid
        :in $ ?attr-id
        :where [?eid ?attr-id]]
      (d/q (:tx-data tx) (:id (d/attribute (:db-after tx) attr-k)))
      ffirst
      (->> (d/entity (:db-after tx)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handlers

(defmulti handle-tx-event
  (fn [action-k tx]
    action-k))

(defmethod handle-tx-event :create-game
  [_ tx]
  (let [seat (get-attr tx :game.seat/player)]
    {:player (seats/ext-form seat)}))

(defmethod handle-tx-event :join-game
  [_ tx]
  (let [seat (get-attr tx :game.seat/player)]
    {:player (seats/ext-form seat)}))

(defmethod handle-tx-event :bid
  [_ tx]
  (let [bid (get-attr tx :game.bid/bid)]
    {:bid {:position (:game.seat/position (:game.bid/seat bid))
           :bid (bids/ext-form bid)}}))

(defmethod handle-tx-event :exchange-kitty
  [_ tx]
  {})

(defmethod handle-tx-event :play-card
  [_ tx]
  (let [play (get-attr tx :trick.play/card)
        seat (:trick.play/seat play)
        card (:trick.play/card play)]
    {:play-card {:position (:game.seat/position seat)
                 :card (cards/ext-form card)}}))

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

(defn find-clients
  [this game-id]
  (get (:games @(:client-db this)) game-id))

(defn for-client
  [this action-k tx client]
  (let [msg (assoc (handle-tx-event action-k tx)
              :action action-k)]
    (clients/publish (:clients this) client msg)))

(defn handle-tx
  [component tx]
  (let [{:keys [log]} component
        game-txs (get-game-id-and-actions tx)]
    (doseq [[game-id action-k] game-txs]
      (let [action-k (keyword (name action-k))]
       (doseq [client (find-clients component game-id)]
         (for-client component action-k tx client))))))

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
  [this game-id client]
  (let [{:keys [log datomic]} this
        conn (:conn datomic)]
    (log/log log {:msg "register-client"
                  :game-id game-id})
    (swap! (:client-db this) update-in [:games game-id] conj client)
    (clients/publish (:clients this) client {:action :registered})
    (doseq [tx (find-game-transactions conn game-id)]
      (let [tx {:tx-data tx
                :db-after (d/db conn)}
            game-tx (first (get-game-id-and-actions tx))
            [game-id action-k] game-tx
            action-k (keyword (name action-k))]
        (for-client this action-k tx client)))))

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
    [:datomic :log :clients]))
