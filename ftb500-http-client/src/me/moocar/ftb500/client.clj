(ns me.moocar.ftb500.client
  (:require [clojure.edn :as edn]
            [clojure.pprint :refer [pprint]]
            [clj-http.client :as http]
            [com.stuartsierra.component :as component]
            [http.async.client :as http-async]
            [me.moocar.log :as log]))

(defn uuid? [s]
  (instance? java.util.UUID s))

(def ref-player-names
  #{"Soap" "Eddy" "Bacon" "Winston" "Big Chris" "Barry the Baptist"
    "Little Chris" "Hatchet Harry" "Willie" "Dog" "Plank" "Nick the Greek"
    "Rory Breaker" "Traffic Warden" "Lenny" "JD"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Requests

(defn send-request
  [client request-method action args]
  (let [response (http/request {:method request-method
                                :url (str (:endpoint client) "/" (name action))
                                :body (pr-str args)
                                :throw-exceptions false})
        status (:status response)]
    (case status
      200 (edn/read-string (:body response))
      400 (let [{:keys [msg data]} (edn/read-string (:body response))]
            (throw (Exception. (str msg ": " data))))
      (throw (ex-info (str (:status response) ": " (:body response)) {})))))

(defn create-player
  [client player-name]
  {:pre [(string? player-name)]}
  (let [response (send-request client
                               :post
                               :create-player
                               {:player-name player-name})
        player-id (:player-id response)]
    (assert player-id)
    (swap! (:db client)
           assoc
           :player-id player-id
           :player-name player-name)
    :done))

(defn subscribe
  [client game-id]
  (http-async/send (:websocket client)
                   :text
                   (pr-str {:action :subscribe
                            :player-id (:player-id @(:db client))
                            :game-id game-id})))

(defn create-game
  [client num-players]
  {:pre [(number? num-players)]}
  (log/log (:log client) {:msg "Create game"
                          :player (:player-name @(:db client))})
  (if-let [player-id (:player-id @(:db client))]
    (let [response (send-request client
                                 :post
                                 :create-game
                                 {:player-id player-id
                                  :num-players num-players})
          game-id (:game-id response)
          cards (:cards response)]
      (assert game-id)
      (swap! (:db client) assoc
             :game-id game-id
             :cards cards)
      (subscribe client game-id)
      :done)
    (throw (ex-info "No player registered. Call :create-player first" {}))))

(defn join-game
  [client game-id]
  {:pre [(uuid? game-id)]}
  (log/log (:log client) {:msg "Joining game"
                          :player (:player-name @(:db client))})
  (if-let [player-id (:player-id @(:db client))]
    (let [response (send-request client
                                 :post
                                 :join-game
                                 {:player-id player-id
                                  :game-id game-id})
          cards (:cards response)]
      (swap! (:db client) assoc
             :game-id game-id
             :cards cards)
      (subscribe client game-id)
      :done)
    (throw (ex-info "No player registered. Call :create-player first" {}))))

(defn bid
  [client bid]
  {:pre [(keyword? bid)]}
  (let [player-id (:player-id @(:db client))
        game-id (:game-id @(:db client))
        response (send-request client
                               :post
                               :bid
                               {:player-id player-id
                                :game-id game-id
                                :bid bid})
        kitty-cards (:kitty-cards response)]
    (when kitty-cards
      (swap! (:db client) assoc
             :kitty-cards kitty-cards))
    :done))

(defn exchange-kitty
  [client cards]
  {:pre [(coll? cards)]}
  (let [player-id (:player-id @(:db client))
        game-id (:game-id @(:db client))
        response (send-request client
                               :post
                               :exchange-kitty
                               {:player-id player-id
                                :game-id game-id
                                :cards cards})]
    :done))

(defn play-card
  ([client card]
     {:pre [(map? card)]}
     (let [player-id (:player-id @(:db client))
           game-id (:game-id @(:db client))
           response (send-request client
                                  :post
                                  :play-card
                                  {:player-id player-id
                                   :game-id game-id
                                   :card card})]
       :done)))

(defn game-view
  [client]
  (let [player-id (:player-id @(:db client))
        game-id (:game-id @(:db client))
        response (send-request client
                               :get
                               :game-view
                               {:player-id player-id
                                :game-id game-id})]
    (swap! (:db client) assoc
           :game-state response)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Events Listener

(defrecord HttpClient [endpoint host port async-client websocket db log]
  component/Lifecycle
  (start [this]
    (let [ws (http-async/websocket async-client
                                   (format "ws://%s:%s/ws/" host port)

                                   :open
                                   (fn [conn]
                                     (log/log log {:msg "received connection"}))

                                   :close
                                   (fn [conn]
                                     (log/log log {:msg "Connection closed"}))

                                   :text
                                   (fn [conn text]
                                     (log/log log {:msg (edn/read-string text)
                                                   :player-name (:player-name @db)})))]
      (assoc this
        :websocket ws)))
  (stop [this]
    this))

(defn new-http-client
  []
  (component/using
    (map->HttpClient {:endpoint "http://localhost:8081"
                      :host "localhost"
                      :port 8081
                      :db (atom {})
                      :async-client (http-async/create-client)})
    [:log]))