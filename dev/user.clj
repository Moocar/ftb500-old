(ns user
  (:require [clojure.core.async :as async]
            [clojure.java.io :as jio]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.game2 :as game2]
            [me.moocar.ftb500.system :as system]))

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
  :ready)

(defn reset
  "Stops the system, reloads modified source files, and restarts it."
  []
  (stop)
  (refresh :after 'user/go))
