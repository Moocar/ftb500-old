(ns me.moocar.ftb500.client.transport.inline.play
  (:require [clojure.core.async :as async :refer [go <! <!! go-loop]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
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
  (component/start (merge engine (inline-client-system/new-system config))))

(defn play
  []
  (let [config (dev-config)
        engine (component/start (engine-system/new-system config))
        clients (repeatedly 4 #(ai/new-client-ai (new-ai-client engine config)))
        log (:log engine)]
    (try
      (let [clients (->> clients
                         (map ai/start)
                         (doall)
                         (map <!!)
                         (doall))]
        (go (<! (async/timeout 2000))
            (log/log log "!!!!!!!!! Timeout and shutdown !!!!!!!!!!")
            (doseq [client clients]
              (component/stop client))
            (component/stop engine))
        (<!!
         (go
           (let [response (<! (client/send! (first clients) :add-game {:num-players 4}))]
             (let [game-id (:game/id (second response))]
               (->> clients
                    (map #(ai/start-playing % game-id))
                    (doall)
                    (map <!!)
                    (doall)))))))
      (catch Throwable t
        (log/log (:log engine) t))
      (finally
        (doseq [client clients]
          (component/stop client))
        (component/stop engine)))

    (doseq [client clients]
      (println)
      (async/alts!! [(go-loop []
                        (when-let [msg (<! (:output-ch (:log client)))]
                          (println msg)
                          (recur)))
                     (async/timeout 1000)]))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.transport.inline.play/play))
