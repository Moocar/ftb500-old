(ns user
  (:require [clojure.core.async :as async]
            [clojure.java.io :as jio]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.engine.system :as system]
            [me.moocar.log :as log]
            [me.moocar.ftb500.db :as db]))

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

(defn play
  []
  )

(defn go
  "Initializes and starts the system running."
  []
  (init)
  (start)
  (let [{:keys [log client1 client2]} system
        _ (client/create-game client1)
        game-id (client/get-game-id client1)
        _ (client/join-game client2 game-id)])
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (db/del-db)
  (stop)
  (refresh :after 'user/go))