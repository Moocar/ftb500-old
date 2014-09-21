(ns me.moocar.ftb500.client.ai
  (:require [clojure.core.async :as async :refer [go <! put!]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.client.transport :as transport]
            [me.moocar.ftb500.game :as game]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn uuid []
  (java.util.UUID/randomUUID))

(defn send!
  ([this route msg dont-send]
     (transport/send! (:client-transport this)
                      {:route route
                       :body msg}))
  ([this route msg]
     (let [response-ch (async/chan)]
       (transport/send! (:client-transport this)
                        {:route route
                         :body msg}
                        1000
                        (fn [response]
                          (put! response-ch response)))
       response-ch)))

(defn start
  [this]
  (let [user-id (uuid)]
    (go
      (and (<! (send! this :signup {:user-id user-id}))
           (<! (send! this :login {:user-id user-id})))
      (assoc this
        :player/id user-id))))

(defn stop
  [this]
  (<! (send! this :logout {})))

(defn game-info
  [this game-id]
  (go (second (<! (send! this :game-info {:game-id game-id})))))

(defn join-game
  [this game]
  (go
    (loop [game game]
      (let [{game-id :game/id} game
            seats (:game/seats game)]
        (if-let [seat (first (filter #(game/my-seat? % this) seats))]
          seat
          (when-let [seat (first (remove #(game/seat-taken? % this) seats))]
            (do (<! (send! this :join-game {:game/id game-id
                                            :seat/id (:seat/id seat)}))
                (recur (<! (game-info this game-id))))))))))

(defn start-playing
  [this game-id]
  (go
    (let [game (<! (game-info this game-id))]
      (let [seat (<! (join-game this game))]
        (when-not seat
          (log this "Game not joined"))))))

(defn new-client-ai [{:keys [log client-transport]}]
  {:log log
   :client-transport client-transport})
