(ns me.moocar.ftb500.client.transport.inline
  (:require [clojure.core.async :as async :refer [put!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.listener :as client-listener]
            [me.moocar.ftb500.client.transport :as client-transport]
            [me.moocar.ftb500.engine.transport.inline :as engine-inline-transport]
            [me.moocar.log :as log]))

(defn- inline-send
  [this message response-ch]
  (let [{:keys [engine-inline-transport client-id]} this]
    (println "putting!")
    (put! (:receive-ch engine-inline-transport)
          (cond-> {:message message
                   :client-id client-id}
                  response-ch (assoc :callback #(put! response-ch %))))))

(defrecord ClientInlineTransport [engine-inline-transport user-store log receive-ch listener client-id]

  component/Lifecycle
  (start [this]
    (if receive-ch
      this
      (let [listener (client-listener/start
                      (client-listener/new-client-listener log))
            {:keys [receive-ch]} listener]
        (engine-inline-transport/connect user-store client-id receive-ch)
        (assoc this
          :receive-ch receive-ch
          :listener listener))))
  (stop [this]
    (if receive-ch
      (do (client-listener/stop listener)
          (assoc this
            :receive-ch nil
            :listener nil))
      this))

  client-transport/ClientTransport
  (-send! [this message]
    (inline-send this message nil))
  (-send! [this message timeout-ms callback]
    (let [response-ch (async/chan)]
      (inline-send this message response-ch)
      (when callback
        (if-let [response (first (async/alts!! [response-ch (async/timeout timeout-ms)]))]
          (callback response)
          (callback :chsk/timeout))))))

(defn new-client-inline-transport []
  (component/using (map->ClientInlineTransport {:client-id (java.util.UUID/randomUUID)})
    [:engine-inline-transport :user-store :log]))
