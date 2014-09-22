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

(def sub-chan-keys
  [:deal-cards :bid :kitty :exchange-kitty :play-card])

(defn sub-to-chans
  [mult]
  (let [my-ch (async/chan)
        pub-ch (async/pub my-ch :route)]
    (async/tap mult my-ch)
    (doall
     (reduce (fn [m sub-ch-key]
               (let [ch (async/chan)]
                 (async/sub pub-ch sub-ch-key ch)
                 (assoc m sub-ch-key ch)))
             {}
             sub-chan-keys))))

(defn start
  [this]
  (let [{:keys [receive-ch]} this
        mult (async/mult receive-ch)
        sub-chans (sub-to-chans mult)
        user-id (uuid)]
    (go
      (and (<! (send! this :signup {:user-id user-id}))
           (<! (send! this :login {:user-id user-id})))
      (assoc this
        :sub-chans sub-chans
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
  (let [{:keys [sub-chans]} this]
   (go
     (let [game (<! (game-info this game-id))
           seat (<! (join-game this game))
           hand (set (:cards (<! (:deal-cards sub-chans))))]
       (let []
         (when-not seat
           (log this "Game not joined")))))))

(defn new-client-ai
  [this]
  (let [{:keys [log client-transport]} this
        {:keys [listener]} client-transport
        {:keys [mult]} listener
        receive-ch (async/chan)]
    (async/tap mult receive-ch)
    (merge this {:receive-ch receive-ch})))
