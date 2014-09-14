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
   [me.moocar.ftb500.engine.datomic :as datomic]
   [me.moocar.ftb500.engine.transport :as engine-transport]
   [me.moocar.ftb500.engine.transport.inline :as engine-inline-transport]
   [me.moocar.ftb500.client.transport :as client-transport]
   [me.moocar.ftb500.client.transport.inline :as client-inline-transport]
   [me.moocar.system.dev.gen-project :as gen-project]
   [me.moocar.log :as log]))

(defn dev-config
  []
  {:datomic {:uri "datomic:free://localhost:4334/ftb500"}})

(def engine-implementations
  #{:engine-inline-transport})

(defn new-engine-system
  [config]
  (component/system-map
   :log (log/new-logger config)
   :engine-inline-transport (engine-inline-transport/new-engine-inline-transport)
   :engine-transport (engine-transport/new-engine-multi-transport (vec engine-implementations))
   :server-listener (engine-transport/new-server-listener)
   :client-inline-transport (client-inline-transport/new-client-inline-transport)
   :client-listener (client-inline-transport/new-client-listener)
   :datomic (datomic/new-datomic-database config)))

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
