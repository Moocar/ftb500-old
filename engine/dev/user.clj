(ns user
  (:require [clojure.core.async :as async]
            [clojure.java.io :as jio]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.games :as games]
            [me.moocar.ftb500.kitty :as kitty]
            [me.moocar.ftb500.players :as players]
            [me.moocar.ftb500.system :as system]
            [me.moocar.ftb500.tricks :as tricks]))

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
  (def p (:players system))
  (def g (:games system))
  :ready)

(defn pg
  "Prints the latest game to console"
  []
  (games/print-game g
                    (first (games/get-game-ids (d/db (:conn (:db g)))))))

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (db/del-db)
  (stop)
  (refresh :after 'user/go)
  (Thread/sleep 10)
  (pg))
