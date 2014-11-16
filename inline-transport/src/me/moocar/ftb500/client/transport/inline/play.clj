(ns me.moocar.ftb500.client.transport.inline.play
  (:require [clojure.core.async :as async :refer [go <! <!! go-loop]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.async :refer [<?]]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.ai :as ai]
            [me.moocar.ftb500.client.transport.inline.system :as inline-client-system]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.schema :as schema]
            [me.moocar.log :as log]))

(defn dev-config
  []
  {:datomic {:uri "datomic:free://localhost:4334/ftb500"}})

(defn new-ai-client
  [engine config]
  (component/start (merge engine (inline-client-system/new-system config))))

(defn <!!all [s]
  (let [responses (->> s
                       (doall)
                       (map <!!)
                       (doall))]
    (if-let [ex (first (filter #(instance? Throwable %) responses))]
      (throw ex)
      responses)))

(defn clients-thread [clients]
  (async/thread
    (try
      (let [response (<!! (client/send! (first clients) :add-game {:num-players 4}))]
        (let [game-id (:game/id (second response))]
          (<!!all (map #(ai/start-playing % game-id) clients))))
      (catch Throwable t t))))

(defn play
  []
  (let [config (dev-config)
        engine (component/start (engine-system/new-system config))
        clients (repeatedly 4 #(ai/new-client-ai (new-ai-client engine config)))
        log (:log engine)]
    (try
      (let [clients (<!!all (map ai/start clients))]

        (go (<! (async/timeout 3000))
            (log/log log "Shutting down engine after bad timeout")
            (component/stop engine))

        (let [timeout (async/timeout 2000) 
              [clients port] (async/alts!! [(clients-thread clients) timeout])]

          (when (instance? Throwable clients)
            (throw clients))
          (log/log log (str "Main client loops finished: " (when (= timeout port) "[Timed out]")))

          (log/log log "Shutting down clients")
          (let [timeout (async/timeout 2000)
                [clients port] (async/alts!! [(async/into [] 
                                                          (async/take (count clients) 
                                                                      (async/merge (map ai/stop clients))))
                                              timeout])]

            (log/log log "Out of alts")
            (doseq [client clients]
              (component/stop client))
            (if (= timeout port)
              (log/log log "Failed to shutdown all clients")
              (log/log log "Successfully shutdown all clients")))))
      (finally
        (log/log log "Shutting down engine")
        (component/stop engine)))

    (doseq [client clients]
      (println)
      (let [timeout (async/timeout 2000) 
            [v port] (async/alts!! [(go-loop []
                                      (if-let [msg (<? (:output-ch (:log client)))]
                                        (do (println msg)
                                            (recur))
                                        true))
                                    timeout])]
        (println "Finished client logs" (= timeout port))))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.transport.inline.play/play))
