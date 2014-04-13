(ns me.moocar.ftb500.client
  (:require [clojure.edn :as edn]
            [clj-http.client :as http]))

(def ref-player-names
  #{"Soap" "Eddy" "Bacon" "Winston" "Big Chris" "Barry the Baptist"
    "Little Chris" "Hatchet Harry" "Willie" "Dog" "Plank" "Nick the Greek"
    "Rory Breaker" "Traffic Warden" "Lenny" "JD"})

(def action-lookup
  {:create-player [:post]})

(defn send-request
  [client action args]
  (let [request-method (get action-lookup action)
        response (http/request {:method request-method
                                :url (str (:endpoint client) "/" (name action))
                                :body (pr-str args)})]
    (edn/read-string (:body response))))

(defn create-player
  [client player-name]
  {:pre [(string? player-name)]}
  (let [response (send-request client :create-player {:player-name player-name})
        player-id (:player-id response)]
    (assert player-id)
    (swap! (:db client) assoc :player-id player-id)
    :done))

(defn create-game
  [client num-players]
  {:pre (number? num-players)}
  (let [response (send-request client
                               :create-game
                               {:player-id (:player-id @(:db client))
                                            :num-players num-players})
        game-id (:game-id response)]
    (assert game-id)
    (swap! (:db client) assoc :current-game-id game-id)
    :done))

(defrecord HttpClient [endpoint db])

(defn new-http-client
  []
  (map->HttpClient {:endpoint "http://localhost:8080"
                    :db (atom {})}))
