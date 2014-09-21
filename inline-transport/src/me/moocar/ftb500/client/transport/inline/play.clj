(ns me.moocar.ftb500.client.transport.inline.play
  (:require [clojure.core.async :as async :refer [go <! <!!]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [me.moocar.log :as log]
            [me.moocar.ftb500.client.transport.inline.system :as inline-client-system]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.ai :as ai]))

(defn dev-config
  []
  {:datomic {:uri "datomic:free://localhost:4334/ftb500"}})

(defn new-ai-client
  [engine config]
  #(component/start (merge engine (inline-client-system/new-system config))))

(defn play
  []
  (let [config (dev-config)
        engine (component/start (engine-system/new-system config))
        clients (repeatedly 4 (new-ai-client engine config))
        log (:log engine)]
    (try
      (let [clients (->> clients
                         (map ai/start)
                         (doall)
                         (map <!!)
                         (doall))]
        (go
          (let [response (<! (client/send! (first clients) :add-game {:num-players 4} true))]
            (let [game-id (:game/id (second response))]
              (->> clients
                   (map #(ai/start-playing % game-id))
                   (doall)
                   (map <!!)
                   (doall))))))
      (catch Throwable t
        (log/log (:log engine) t))
      (finally
        (<!! (async/timeout 500))
        (doseq [client clients]
          (component/stop client))
        (component/stop engine)))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.transport.inline.play/play))
