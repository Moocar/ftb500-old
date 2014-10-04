(ns me.moocar.ftb500.client.ai
  (:require [clojure.core.async :as async :refer [go <! put!]]
            [clojure.set :refer [rename-keys]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.transport :as transport]
            [me.moocar.ftb500.client.ai.bids :as bids]
            [me.moocar.ftb500.game :as game]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn uuid []
  (java.util.UUID/randomUUID))

(defn send!
  ([this route msg dont-send]
     (client/send! this route msg dont-send))
  ([this route msg]
     (client/send! this route msg)))

(defn start
  [this]
  (let [{:keys [receive-ch]} this
        mult (async/mult receive-ch)
        tapped-ch (async/chan)
        route-pub-ch (async/pub tapped-ch :route)
        user-id (uuid)]
    (async/tap mult tapped-ch)
    (go
      (and (<! (send! this :signup {:user-id user-id}))
           (<! (send! this :login {:user-id user-id})))
      (assoc this
        :route-pub-ch route-pub-ch
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

(defn get-deal-cards
  [game deal-cards]
  (merge game
         (-> (:body deal-cards)
             (rename-keys {:cards :hand}))))

(defn start-playing
  [this game-id]
  (let [{:keys [route-pub-ch]} this
        join-game-ch (async/chan)
        deal-cards-ch (async/chan)]
    (async/sub route-pub-ch :join-game join-game-ch)
    (async/sub route-pub-ch :deal-cards deal-cards-ch)
    (go
      (-> (<! (game-info this game-id))
          (as-> game
                (assoc game :bid-table (<! (send! this :bid-table {})))
                (assoc game :num-players (count (:game/seats game)))
                (assoc game :seat (<! (join-game this game)))
                (assoc game :seats (->> join-game-ch
                                        (async/take (:num-players game))
                                        (async/into [])
                                        (<!)
                                        (map :body)))
                (get-deal-cards game (<! deal-cards-ch))
                (assoc game :game/bids (<! (bids/start this game))))))))

(defn new-client-ai
  [this]
  (let [{:keys [log client-transport]} this
        {:keys [listener]} client-transport
        {:keys [mult]} listener
        receive-ch (async/chan)]
    (async/tap mult receive-ch)
    (merge this {:receive-ch receive-ch})))
