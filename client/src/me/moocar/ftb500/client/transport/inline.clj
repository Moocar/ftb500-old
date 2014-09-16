(ns me.moocar.ftb500.client.transport.inline
  (:require [clojure.core.async :as async :refer [put!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.listener :as client-listener]
            [me.moocar.ftb500.client.transport :as client-transport]
            [me.moocar.log :as log]))

(defn- inline-send
  [this message response-ch]
  (let [{:keys [route msg]} message
        {:keys [user-id-atom engine-inline-transport server-listener client-listener log]} this
        {:keys [client-receive-chs]} engine-inline-transport]
    (case route

      :login
      (let [user-id (:user-id msg)]
        (reset! user-id-atom user-id)
        (swap! client-receive-chs assoc user-id (:receive-ch client-listener))
        (log/log log "Login successful!")
        (put! response-ch :success))

      :logout
      (let [user-id (deref user-id-atom)]
        (swap! client-receive-chs dissoc user-id)
        (reset! user-id-atom nil)
        (log/log log "Logout successful!")
        (put! response-ch :success))

      ;; Else it's a normal request, pass it to the server
      (let [user-id (deref user-id-atom)]
        (put! (:receive-ch server-listener)
              {:user-id user-id
               :msg msg
               :route route
               :callback #(put! response-ch %)})))))

(defrecord ClientInlineTransport [server-listener engine-inline-transport
                                  client-listener user-id-atom]
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
  (component/using (map->ClientInlineTransport {:user-id-atom (atom nil)})
    [:server-listener :engine-inline-transport :client-listener :log]))

(defrecord ClientListener [log receive-ch listener]
  component/Lifecycle
  (start [this]
    (if receive-ch
      this
      (let [listener (client-listener/start
                      (client-listener/new-client-listener log))
            {:keys [receive-ch]} listener]
        (assoc this
          :receive-ch receive-ch
          :listener listener))))
  (stop [this]
    (if receive-ch
      (do (client-listener/stop listener)
          (assoc this
            :receive-ch nil
            :listener nil))
      this)))

(defn new-client-listener []
  (component/using (map->ClientListener {})
    [:log]))
