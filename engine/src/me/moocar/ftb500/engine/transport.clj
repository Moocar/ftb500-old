(ns me.moocar.ftb500.engine.transport
  (:require [com.stuartsierra.component :as component]
            [me.moocar.log :as log]))

(defprotocol EngineTransport
  (-send! [this user-id msg]))

(defrecord EngineMultiTransport [transports]
  EngineTransport
  (send! [this user-id msg]
    (doseq [transport transports]
      (-send! transport user-id msg))))

(def engine-implementations
  #{:engine-inline-transport})

(defn new-engine-multi-transport []
  (component/using (map->EngineMultiTransport {})
    engine-implementations))

(defn send!
  "Sends the message to the user. The user can be connected by many
  underlying transports and all will receive the message. E.g a user
  might be connected by ssh and sente"
  [transport user-id msg]
  (-send! transport user-id msg))

(defrecord ServerListener [engine-receive-ch log]
  component/Lifecycle
  (start [this]
    (let [loop-ch
          (go-loop []
            (when-let [full-msg (<! engine-receive-ch)]
              (let [{:keys [user msg]} full-msg]
                (log/log log (str "received" full-msg)))
              (recur)))])))
