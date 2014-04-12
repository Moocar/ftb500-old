(ns me.moocar.ftb500.clients.channel-client)

(defrecord ChannelClient [action-ch listen-ch])

(defn )

(defn new-game!
  [client num-players]
  (let [action-ch (:action-ch client)]
    (go (>! action-ch {:action :new-game
                       :args {:num-players 4}})
        (<! action-ch))))
