(ns me.moocar.ftb500.client.transport.inline.play
  (:require [clojure.core.async :as async :refer [go <! <!!]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [me.moocar.log :as log]
            [me.moocar.ftb500.client.transport.inline.system :as inline-client-system]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.client :as client]))

(defn dev-config
  []
  {:datomic {:uri "datomic:free://localhost:4334/ftb500"}})

(defn signup-and-login [client]
  (go
    (let [user-id (java.util.UUID/randomUUID)]
      (when (<! (client/send! client :signup {:user-id user-id} true))
        (client/send! client :login {:user-id user-id} true)))))

(defn play
  []
  (let [config (dev-config)
        engine (component/start (engine-system/new-system config))
        clients (repeatedly 10 #(component/start (merge engine (inline-client-system/new-system config))))
        log (:log engine)]
    (try
      (->> clients
           (map signup-and-login)
           (doall)
           (map <!!)
           (doall))
      (go
        (let [response (<! (client/send! (first clients) :add-game {:num-players 4} true))]
          (log/log log response)
          (let [game-id (:game/id (second response))
                game (second (<! (client/send! (first clients) :game-info {:game-id game-id} true)))]
            (log/log log game))))
      (catch Throwable t
        (log/log (:log engine) t))
      (finally
        (<!! (async/timeout 500))
        (doseq [client clients]
          (component/stop client))
        (component/stop engine)))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.transport.inline.play/play))
