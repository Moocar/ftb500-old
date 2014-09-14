(ns me.moocar.ftb500.engine.transport
  (:require [clojure.core.async :as async :refer [go-loop <!]]
            [com.stuartsierra.component :as component]
            [me.moocar.log :as log]))

(defprotocol EngineTransport
  (-send! [this user-id msg]))

(defrecord EngineMultiTransport [transports]
  EngineTransport
  (-send! [this user-id msg]
    (doseq [transport-k transports]
      (let [transport (get this transport-k)]
        (-send! transport user-id msg)))))

(defn new-engine-multi-transport [transports]
  (component/using (map->EngineMultiTransport {:transports transports})
    transports))

(defn send!
  "Sends the message to the user. The user can be connected by many
  underlying transports and all will receive the message. E.g a user
  might be connected by ssh and sente"
  [transport user-id msg]
  (-send! transport user-id msg))

(defrecord ServerListener [log receive-ch]
  component/Lifecycle
  (start [this]
    (if receive-ch
      this
      (let [receive-ch (async/chan)]
        (go-loop []
          (when-let [full-msg (<! receive-ch)]
            (let [{:keys [user msg]} full-msg]
              (log/log log (str "Server received: " full-msg)))
            (recur)))
        (assoc this
          :receive-ch receive-ch))))
  (stop [this]
    (if receive-ch
      (do (async/close! receive-ch)
          (assoc this :receive-ch nil))
      this)))

(defn new-server-listener []
  (component/using (map->ServerListener {})
    [:log]))
