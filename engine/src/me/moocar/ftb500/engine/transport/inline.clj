(ns me.moocar.ftb500.engine.transport.inline
  (:require [clojure.core.async :as async :refer [go-loop <! >! put!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.transport :as engine-transport]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]))

(defn connect
  [this client-id client-receive-ch]
  (let [{:keys [connected-clients-atom]} this]
    (swap! connected-clients-atom
           assoc-in
           [client-id :receive-ch]
           client-receive-ch)))

(defn client-user-id
  [user-store client-id]
  (let [{:keys [connected-clients-atom]} user-store]
    (:user-id (get (deref connected-clients-atom) client-id))))

(defn user-receive-chans
  [user-store user-id]
  (let [{:keys [user-ids-atom connected-clients-atom]} user-store
        user-ids (deref user-ids-atom)
        connected-clients (deref connected-clients-atom)
        client-ids (get user-ids user-id)]
    (map (fn [client-id]
           (:receive-ch (get connected-clients client-id)))
         client-ids)))

(defrecord InlineUserStore [connected-clients-atom user-ids-atom]
  user-store/UserStore
  (write [this client-id user-id]
    (swap! connected-clients-atom assoc-in [client-id :user-id] user-id)
    (swap! user-ids-atom
           update-in
           [user-id]
           (fn [client-ids]
             (if client-ids
               (conj client-ids client-id)
               #{client-id}))))
  (delete [this client-id]
    (let [user-id (:user-id (get (deref connected-clients-atom) client-id))]
      (swap! connected-clients-atom update-in [client-id] dissoc :user-id)
      (swap! user-ids-atom update-in [user-id] disj client-id))))

(defn new-inline-user-store []
  (map->InlineUserStore {:connected-clients-atom (atom {})
                         :user-ids-atom (atom {})}))

(defrecord EngineInlineTransport [server-listener user-store receive-ch]

  component/Lifecycle
  (start [this]
    (if receive-ch
      this
      (let [receive-ch (async/chan)]
        (go-loop []
          (when-let [request (<! receive-ch)]
            (let [{:keys [client-id message callback]} request
                  {:keys [route body]} message
                  user-id (client-user-id user-store client-id)]
              (>! (:receive-ch server-listener)
                  (cond-> {:route route
                           :client-id client-id
                           :body body}
                          callback (assoc :callback callback)
                          user-id  (assoc :logged-in-user-id user-id))))
            (recur)))
        (assoc this
          :receive-ch receive-ch
          :client-receive-chs (atom {})))))
  (stop [this]
    (if receive-ch
      (do
        (async/close! receive-ch)
        (assoc this :receive-ch nil))
      this)))

(defn new-engine-inline-transport []
  (component/using
    (map->EngineInlineTransport {})
    [:server-listener :user-store]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Sender

(defrecord EngineInlineSender [user-store]
  engine-transport/EngineTransport
  (-send! [this user-id msg]
    (doseq [client-receive-ch (user-receive-chans user-store user-id)]
      (put! client-receive-ch msg))))

(defn new-engine-inline-sender []
  (component/using (map->EngineInlineSender {})
    [:user-store]))
