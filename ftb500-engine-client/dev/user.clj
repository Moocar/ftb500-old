(ns user
  (:require [clojure.core.async :as async]
            [clojure.java.io :as jio]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.engine.system :as system]
            [me.moocar.ftb500.client.transport :as transport]
            [me.moocar.ftb500.client.engine.autoplay :as autoplay]
            [me.moocar.log :as log]
            [me.moocar.ftb500.db :as db]))

(def system nil)

(defn init
  []
  (alter-var-root #'system (constantly (system/new-autoplay-system))))

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
  (autoplay/play (map :client (:clients system)))
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (db/del-db)
  (stop)
  (refresh :after 'user/go))
