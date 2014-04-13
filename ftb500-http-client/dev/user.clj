(ns user
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.system :as system]))

(def system nil)

(defn init
  []
  (alter-var-root #'system (constantly (system/new-system))))

(defn start
  []
  (alter-var-root #'system component/start))

(defn stop
  []
  (alter-var-root #'system #(when % (component/stop %))))

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  (def clients (:clients system))
  (def fifth-client (client/new-http-client))
  (doseq [[c n] (map vector clients (take 4 (shuffle client/ref-player-names)))]
    (println "creating player" n)
    (client/create-player c n))
  (client/create-player fifth-client "Fifth Man")
  (let [game-owner (first clients)]
    (println "creating game")
    (client/create-game game-owner 4)
    (let [game-id (:current-game-id @(:db game-owner))]
      (doseq [c (rest clients)]
        (println "joining game")
        (client/join-game c game-id))))
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))
