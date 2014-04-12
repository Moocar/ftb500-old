(ns user
  (:require [clojure.java.io :as jio]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [clj-http.client :as http]))

(def system nil)

(defn init
  []
  (alter-var-root #'system (constantly {:db (atom {})
                                        :endpoint "http://localhost:8080"})))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (refresh :after 'user/go))

(defn create-player
  [system player-name]
  (let [response (http/post (str (:endpoint system) "/create-player")
                            {:body (pr-str {:player-name player-name})})]
    (println "response" response)))
