(ns me.moocar.ftb500.client
  (:require [clojure.core.async :refer [chan <! go-loop]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.protocols :as protocols]
            [me.moocar.log :as log]))

(def ref-player-names
  #{"Soap" "Eddy" "Bacon" "Winston" "Big Chris" "Barry the Baptist"
    "Little Chris" "Hatchet Harry" "Willie" "Dog" "Plank" "Nick the Greek"
    "Rory Breaker" "Traffic Warden" "Lenny" "JD"})

(defn get-game-id
  [this]
  (:game-id @(:db this)))

(defn debug
  [this msg]
  (log/log (:log this)
           (assoc msg
             :player (:player-name this))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Subscribe

(defmulti handle-msg
  (fn [this msg]
    (:action msg)))

(defmethod handle-msg :registered
  [this msg]
  (debug this {:im :registered}))

(defmethod handle-msg :join-game
  [this msg])

(defn subscribe
  [this]
  (let [{:keys [log requester]} this
        ch (chan)]
    (go-loop []
      (when-let [msg (<! ch)]
        (debug this {:msg-recv msg})
        (handle-msg this msg)
        (recur)))
    (protocols/subscribe requester (get-game-id this) ch)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Requests

(defn send-request
  "Sends a request and throws an exception if non 200 response"
  [this request]
  (let [response (protocols/send-request (:requester this) request)]
    (when-not (= 200 (:status response))
      (throw (ex-info "Non 200 response"
                      {:request request
                       :response response})))
    response))

(defn create-player
  [this]
  (let [request {:action :create-player
                 :args {:player-name (:player-name this)}}
        response (send-request this request)
        player-id (:player-id (:body response))]
    (swap! (:db this) assoc :player-id player-id)))

(defn create-game
  [this]
  (let [request {:action :create-game
                 :args {:player-id (:player-id @(:db this))
                        :num-players 4}}
        response (send-request this request)
        game-id (:game-id (:body response))]
    (swap! (:db this) assoc :game-id game-id)
    (subscribe this)))

(defn join-game
  [this game-id]
  (let [request {:action :join-game
                 :args {:player-id (:player-id @(:db this))
                        :game-id game-id}}
        response (send-request this request)
        cards (:cards (:body response))]
    (swap! (:db this)
           assoc
           :cards cards
           :game-id game-id)
    (subscribe this)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component

(defrecord Client [requester log player-name db]
  component/Lifecycle
  (start [this]
    (create-player this)
    this)
  (stop [this]
    this))

(defn new-client
  [config]
  (let [{:keys [player-name]} config
        player-name (or player-name (rand-nth (vec ref-player-names)))]
    (component/using (map->Client {:db (atom {})
                                   :player-name player-name})
      [:requester :log])))
