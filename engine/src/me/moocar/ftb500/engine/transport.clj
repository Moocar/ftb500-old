(ns me.moocar.ftb500.engine.transport)

(defprotocol EngineTransport
  (-send! [this user-id msg]))

(defrecord EngineMultiTransport [transports]
  EngineTransport
  (send! [this user-id msg]
    (doseq [transport transports]
      (-send! transport user-id msg))))

(defn send!
  "Sends the message to the user. The user can be connected by many
  underlying transports and all will receive the message. E.g a user
  might be connected by ssh and sente"
  [transport user-id msg]
  (-send! transport user-id msg))

(defn start-listen-loop
  [this]
  (let [{:keys [engine-receive-ch]} this]
    (go-loop [msg (<! engine-receive-ch)]
      (let [{:keys [user msg]} msg]
        ;; Do stuff
        ))))
