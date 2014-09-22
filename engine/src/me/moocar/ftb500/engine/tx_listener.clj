(ns me.moocar.ftb500.engine.tx-listener
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.transport :as transport]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]))

(defn- log
  [this msg]
  (log/log (:log this) msg))

(defn handle-tx-event
  [this user-ids action-k tx]
  (let [{:keys [tx-handlers]} this
        tx-handler-keyword (keyword "tx-handler" (name action-k))]
    (let [tx-handler (get tx-handlers tx-handler-keyword)]
      (tx-handler/handle tx-handler user-ids tx))))

(defn get-ident
  [tx entities]
  (update-in entities
             [1]
             (fn [i]
               (d/ident (:db-after tx) i))))

(defn get-game-id-and-actions
  [tx]
  (-> '[:find ?game-id ?action-k ?tx
        :in $ ?game-id-attr ?db-instant-attr ?action-attr
        :where [_ ?game-id-attr ?game-id ?tx]
               [?tx ?db-instant-attr]
               [?tx ?action-attr ?action-k]]
      (d/q (:tx-data tx)
           (:id (d/attribute (:db-after tx) :tx/game-id))
           (:id (d/attribute (:db-after tx) :db/txInstant))
           (:id (d/attribute (:db-after tx) :action)))
      (->> (map #(get-ident tx %))
           (sort-by #(nth % 2)))))

(defn find-connected-users-for-game
  [this game-id]
  (get (:games (deref (:users-atom this))) game-id))

(defn handle-tx
  [this tx]
  (let [{:keys [log]} this
        game-txs (get-game-id-and-actions tx)]
    (doseq [[game-id action-k instant] game-txs]
      (let [action-k (keyword (name action-k))
            user-ids (find-connected-users-for-game this game-id)]
        (when-not (empty? user-ids)
          (handle-tx-event this user-ids action-k tx))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TX Registration

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

(defn register-user-for-game
  [this game-id user-id]
  (let [{:keys [datomic users-atom engine-transport]} this
        conn (:conn datomic)]
    (swap! users-atom
           update-in
           [:games game-id]
           (fn [user-ids]
             (conj (set user-ids) user-id)))
    (transport/send! engine-transport user-id {:action :registered})
    (doseq [tx (find-game-transactions conn game-id)]
      (let [tx {:tx-data tx
                :db-after (d/db conn)}
            game-tx (first (get-game-id-and-actions tx))
            [game-id action-k] game-tx]
        (let [action-k (keyword (name action-k))]
          (handle-tx-event this [user-id] action-k tx))))))

(defrecord TxListener [datomic engine-transport log users-atom tx-report-queue]
  component/Lifecycle
  (start [this]
    (if tx-report-queue
      this
      (let [{:keys [conn]} datomic
            tx-report-queue (d/tx-report-queue conn)]
        (async/thread
          (loop [tx (.take tx-report-queue)]
            (try
              (handle-tx this tx)
              (catch Throwable e
                (log/log log
                         {:msg "error in pubsub loop"
                          :ex e})))
            (recur (.take tx-report-queue))))
        (assoc this
          :tx-report-queue tx-report-queue))))
  (stop [this]
    (if tx-report-queue
      (do
        (d/remove-tx-report-queue (:conn datomic))
        (assoc this :tx-report-queue nil))
      this)))

(defn new-tx-listener
  []
  (component/using (map->TxListener {:users-atom (atom {:games {}})})
    [:datomic :engine-transport :log :tx-handlers]))
