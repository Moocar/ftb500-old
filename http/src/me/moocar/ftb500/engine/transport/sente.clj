(ns me.moocar.ftb500.engine.transport.sente
  (:require [clojure.core.async :as async :refer [go-loop <! >!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.transport :as engine-transport]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]
            [ring.middleware.session.store :as session-store]
            [taoensso.sente :as sente]))

(defrecord SenteUserStore [session-store]
  user-store/UserStore
  (write [this client-id user-id]
    (session-store/write-session session-store client-id {:uid user-id}))
  (delete [this client-id]
    (session-store/write-session session-store client-id {})))

(defn new-sente-user-store []
  (component/using (map->SenteUserStore {})
    [:session-store]))

(defrecord SenteTransport [server-listener

                           ;; fns added from `sente/make-channel-socket!`
                           send-fn ajax-post-fn ajax-get-or-ws-handshake-fn]

  component/Lifecycle
  (start [this]
    (let [sente (sente/make-channel-socket! {})
          {:keys [ch-recv]} sente
          {:keys [receive-ch]} server-listener]
      (go-loop []
        (when-let [event-msg (<! ch-recv)]
          (let [{:keys [ring-req event ?reply-fn]} event-msg
                [ev-id ev-data] event
                route ev-id
                {:keys [session]} ring-req
                {:keys [uid]} session]
            (>! receive-ch
                (cond-> {:route route
                         :client-id (get-in ring-req [:cookies :cookie :value])
                         :body ev-data}
                        ?reply-fn (assoc :callback ?reply-fn)
                        uid       (assoc :logged-in-user-id uid))))))
      (merge this sente)))
  (stop [this]
    (assoc this
      :send-fn nil))

  engine-transport/EngineTransport
  (-send! [this user-id msg]
    (send-fn user-id msg)))

(defn new-sente-transport []
  (component/using (map->SenteTransport {})
    [:server-listener]))
