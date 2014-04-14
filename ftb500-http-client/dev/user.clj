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
    (let [game-id (:game-id @(:db game-owner))]
      (doseq [c (rest clients)]
        (println "joining game")
        (client/join-game c game-id)))
    (client/bid (first clients) :six-clubs)
    (client/bid (second clients) :seven-hearts)
    (client/bid (nth clients 2) :pass)
    (client/bid (nth clients 3) :eight-clubs)
    (client/bid (nth clients 0) :pass)
    (client/bid (nth clients 1) :eight-hearts)
    (client/bid (nth clients 3) :pass)

    (client/exchange-kitty (nth clients 1) (take 3 (:cards @(:db (nth clients 1)))))
    (client/play-card (nth clients 1) (first (:cards @(:db (nth clients 1)))))
    )
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))
