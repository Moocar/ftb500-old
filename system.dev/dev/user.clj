(ns user
  "Tools for interactive development with the REPL. This file should
  not be included in a production build of the application."
  (:require
   [clojure.java.io :as io]
   [clojure.java.javadoc :refer [javadoc]]
   [clojure.pprint :refer [pprint]]
   [clojure.reflect :refer [reflect]]
   [clojure.repl :refer [apropos dir doc find-doc pst source]]
   [clojure.set :as set]
   [clojure.string :as str]
   [clojure.test :as test]
   [clojure.tools.namespace.repl :refer [refresh refresh-all]]
   [com.stuartsierra.component :as component]
   [me.moocar.ftb500.client :as client]
   [me.moocar.ftb500.client.transport :as client-transport]
   [me.moocar.ftb500.client.transport.inline.system :as inline-client-system]
   [me.moocar.ftb500.engine.system :as engine-system]
   [me.moocar.system.dev.gen-project :as gen-project]
   [me.moocar.log :as log]))

(defn dev-config
  []
  {:datomic {:uri "datomic:free://localhost:4334/ftb500"}})

(defn new-engine-system
  [config]
  (merge (engine-system/new-system config)
         (inline-client-system/new-system config)))

(def system nil)

(defn init
  []
  (alter-var-root
   #'system
   (constantly (new-engine-system (dev-config))))
  :ok)

(defn start
  []
  (alter-var-root #'system component/start)
  :ok)

(defn stop
  []
  (alter-var-root #'system #(when % (component/stop %)))
  :ok)

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
