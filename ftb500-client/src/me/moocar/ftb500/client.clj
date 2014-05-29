(ns me.moocar.ftb500.client
  (:require [clojure.core.async :refer [chan <! <!! go-loop go]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport :as transport]
            [me.moocar.ftb500.protocols :as protocols]
            [me.moocar.log :as log]))

(def ref-player-names
  #{"Soap" "Eddy" "Bacon" "Winston" "Big Chris" "Barry the Baptist"
    "Little Chris" "Hatchet Harry" "Willie" "Dog" "Plank" "Nick the Greek"
    "Rory Breaker" "Traffic Warden" "Lenny" "JD"})

(defn get-game-id
  [this]
  (:game-id @(:db this)))

(defn get-player-id
  [this]
  (:player-id @(:db this)))

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

(defmethod handle-msg :create-game
  [this msg]
  (let [player (:player msg)
        position (:position player)]
    (swap! (:db this) update-in [:seats] conj player)))

(defmethod handle-msg :join-game
  [this msg]
  (let [player (:player msg)
        position (:position player)]
    (swap! (:db this) update-in [:seats] conj player)))

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
;; Request helpers

(defn send-request
  "Sends a request and throws an exception if non 200 response"
  [this request]
  (go
    (let [response (<! (transport/request (:transport this) request 10000))]
      (if (= :success (:status response))
        (:body response)
        (if (nil? response)
          (ex-info "Timeout out creating player" {})
          (ex-info "Bad status. Fix it" response))))))

(defn make-player-request
  [this action args]
  {:action action
   :args (merge {:player-id (:player-id @(:db this))}
                args)})

(defn make-game-request
  [this action args]
  {:action action
   :args (merge {:player-id (:player-id @(:db this))
                 :game-id (:game-id @(:db this))}
                args)})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Requests

(defn create-player
  [this]
  (let [request {:action :create-player
                 :args {:player-name (:player-name this)}}]
    (go
      (try
        (let [response (<! (transport/request (:transport this) request 10000))]
          (assert (:status response))
          (if (= :success (:status response))
            (let [player-id (:player-id (:body response))]
              (assert player-id)
              (swap! (:db this) assoc :player-id player-id))
            (if (nil? response)
              (ex-info "Timeout out creating player" {})
              (ex-info "Bad status. Fix it" response))))
        (catch Throwable t
          (.printStackTrace t)
          t)))))

(defn create-game
  [this]
  (let [request (make-player-request this
                                     :create-game
                                     {:num-players 4})]
    (go
      (try
        (let [response (<! (transport/request (:transport this) request 10000))]
          (assert (:status response))
          (if (= :success (:status response))
            (let [game-id (:game-id (:body response))]
              (assert game-id)
              (swap! (:db this) assoc :game-id game-id))
            (if (nil? response)
              (ex-info "Timeout out creating player" {})
              (ex-info "Bad status. Fix it" response))))
        (catch Throwable t
          (.printStackTrace t)
          t)))))

(defn join-game
  [this game-id]
  (let [request (make-player-request this
                                     :join-game
                                     {:game-id game-id})]
    (go
      (try
        (let [response (<! (transport/request (:transport this) request 10000))]
          (assert (:status response))
          (if (= :success (:status response))
            (let [cards (:cards (:body response))]
              (assert cards)
              (swap! (:db this)
                     assoc
                     :cards cards
                     :game-id game-id)
              (swap! (:db this) assoc :game-id game-id))
            (if (nil? response)
              (ex-info "Timeout out creating player" {})
              (ex-info "Bad status. Fix it" response))))
        (catch Throwable t
          (.printStackTrace t)
          t)))))

(defn bid
  [this bid]
  (let [request (make-game-request this :bid {:bid bid})
        response (send-request this request)]
    response))

(defn start-listen-loop
  [this]
  (go-loop []
    (when-let [payload (<! (:client-recv-ch this))]
      (log/log (:log this) {:recv payload})
      (recur))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component

(defrecord Client [transport log player-name db client-recv-ch]
  component/Lifecycle
  (start [this]
    (start-listen-loop this)
    (<!! (create-player this))
    this)
  (stop [this]
    this))

(defn new-client
  [config]
  (let [{:keys [player-name]} config
        player-name (or player-name (rand-nth (vec ref-player-names)))]
    (component/using (map->Client {:db (atom {})
                                   :player-name player-name
                                   :seats []})
      {:transport :transport
       :client-recv-ch :client-recv-ch
       :log :log})))
