(ns me.moocar.ftb500.client.ai
  (:require [clojure.core.async :as async :refer [go <! put! go-loop]]
            [clojure.set :refer [rename-keys]]
            [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.client :as client :refer [send!]]
            [me.moocar.ftb500.client.ai.bids :as bids]
            [me.moocar.ftb500.client.ai.tricks :as tricks]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.lang :refer [uuid]]))

(defn log [this msg]
  (async/put! (:log-ch this) msg))

(defn start
  [this]
  (if (:player this)
    this
    (let [{:keys [receive-ch]} this
          mult (async/mult receive-ch)
          tapped-ch (async/chan)
          route-pub-ch (async/pub tapped-ch :route)
          user-id (uuid)]
      (async/tap mult tapped-ch)
      (go-try
        (and (<? (send! this :signup {:user-id user-id}))
             (<? (send! this :login {:user-id user-id})))
        (assoc this
          :route-pub-ch route-pub-ch
          :player {:player/name "Anthony"
                   :user/id user-id})))))

(defn stop
  [ai]
  {:pre [(ai? ai)]}
  (go-try
   (log ai "Stopping ai client")
   (<? (send! ai :logout {}))
   (log ai "Stopped ai client")
   ai))

(defn start-playing
  "Join a game, wait for all players to join, run the bidding round,
  and then the tricks round"
  [ai game-id]
  {:pre [(:engine-transport ai)]}
  (go-try
   (-> (client/ready-game ai game-id)
       <?
       (as-> ai
           (client/join-game-and-wait-for-others ai (client/join-game ai)))
       <?
       (bids/start)
       <?
       (tricks/start)
       <?)))

(defn new-client-ai
  [this]
  (let [{:keys [engine-transport]} this
        {:keys [listener]} engine-transport
        {:keys [mult]} listener
        receive-ch (async/chan)]
    (async/tap mult receive-ch)
    (merge this {:receive-ch receive-ch})))
