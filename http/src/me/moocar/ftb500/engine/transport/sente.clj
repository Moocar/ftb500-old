(ns me.moocar.ftb500.engine.transport.sente
  (:require [clojure.core.async :as async :refer [go-loop <! >!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.transport :as engine-transport]
            [ring.middleware.session.store :as session-store]
            [taoensso.sente :as sente]))

(defrecord SenteTransport [server-listener session-store

                           ;; fns added from `sente/make-channel-socket!`
                           send-fn ajax-post-fn ajax-get-or-ws-handshake-fn]

  component/Lifecycle
  (start [this]
    (let [sente (sente/make-channel-socket! {})
          {:keys [ch-recv]} sente]
      (go-loop []
        (let [event-msg (<! ch-recv)
              {:keys [ring-req event ?reply-fn]} event-msg
              [ev-id ev-data] event
              route ev-id
              {:keys [session]} ring-req
              {:keys [uid]} session]
          (case route

            :login
            (let [user-id (:user-id ev-data)]
              (session-store/write-session session-store :uid user-id)
              (when ?reply-fn
                (?reply-fn :success)))

            :logout
            (do (session-store/delete-session session-store :uid)
                (when ?reply-fn
                  (?reply-fn :success)))

            ;; Else it's a normal request. Pass it to the server
            (>! (:receive-ch server-listener)
                {:user-id uid
                 :msg ev-data
                 :route route
                 :callback ?reply-fn}))))
      (merge this sente)))
  (stop [this]
    (assoc this
      :send-fn nil))

  engine-transport/EngineTransport
  (-send! [this user-id msg]
    (send-fn user-id msg)))

(defn new-sente-transport []
  (component/using (map->SenteTransport {})
    [:server-listener :session-store]))
