(ns me.moocar.ftb500.engine.tx-listener
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.async :as moo-async]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]))

(defn handle-tx-event
  [this user-ids action-k tx]
  (let [{:keys [tx-handlers]} this
        tx-handler-keyword (keyword "tx-handler" (name action-k))]
    (if-let [tx-handler (get tx-handlers tx-handler-keyword)]
      (tx-handler/handle tx-handler user-ids tx)
      (throw (ex-info "tx handler not present" {:action action-k})))))

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
  (let [game-txs (get-game-id-and-actions tx)]
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
      (->> (sort-by first))))

(defn send-request
  [{:keys [user-store] :as tx-listener}
   user-id
   body]
  (doseq [conn (user-store/user-conns user-store user-id)]
    (moo-async/send-off! (:send-ch conn) body)))

(defn register-user-for-game
  [this game-id user-id]
  {:pre [game-id user-id]}
  (let [{:keys [datomic users-atom]} this
        conn (:conn datomic)]
    (swap! users-atom
           update-in
           [:games game-id]
           (fn [user-ids]
             (conj (set user-ids) user-id)))
    (send-request this user-id {:route :registered})
    (doseq [tx (find-game-transactions conn game-id)]
      (let [tx (tx->datoms conn (first tx))
            tx {:tx-data tx
                      :db-after (d/db conn)}
            game-tx (first (get-game-id-and-actions tx))
            [game-id action-k] game-tx]
        (let [action-k (keyword (name action-k))]
          (handle-tx-event this [user-id] action-k tx))))))

(defrecord TxListener [datomic log-ch users-atom tx-report-queue run-thread shutting-down?]
  component/Lifecycle
  (start [this]
    (if tx-report-queue
      this
      (let [{:keys [conn]} datomic
            tx-report-queue (d/tx-report-queue conn)
            run-thread
            (async/thread
              (try
                (loop []
                  (let [tx (.take tx-report-queue)]
                    (if (= tx :finished)
                      :finished
                      (do
                        (try
                          (handle-tx this tx)
                          (catch Throwable e
                            (async/put! log-ch
                                        {:msg "error in pubsub loop"
                                         :ex e})))
                        (recur)))))
                (catch InterruptedException e
                  :finished)))]
        (assoc this
          :tx-report-queue tx-report-queue
          :run-thread run-thread))))
  (stop [this]
    (if (and tx-report-queue (not @shutting-down?))
      (do
        (try
          (async/put! log-ch "Shutting down tx listener")
          (reset! shutting-down? true)
          (d/remove-tx-report-queue (:conn datomic))
          (.add tx-report-queue :finished)
          (let [timeout (async/timeout 10000)
                [_ port] (async/alts!! [run-thread timeout])]
            (if (= timeout port)
              (async/put! log-ch "Timed out waiting for tx listener thread to finish")
              (async/put! log-ch "TxListener shutdown successfully: ")))
          (finally
            (reset! shutting-down? false)))
        (assoc this :tx-report-queue nil))
      this)))

(defn new-tx-listener
  []
  (component/using (map->TxListener {:users-atom (atom {:games {}})
                                     :shutting-down? (atom false)})
    [:datomic :log-ch :tx-handlers :user-store]))
