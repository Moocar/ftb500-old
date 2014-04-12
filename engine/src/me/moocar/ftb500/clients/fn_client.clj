(ns me.moocar.ftb500.clients.fn-client)

(defn create-player
  [this name]
  (let [response (handlers/handle-request)]
   (swap! (:db this) assoc-in :player-id )))
