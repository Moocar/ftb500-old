(ns me.moocar.ftb500.autoplay
  (:require [clojure.core.async :as async :refer [go <! <!! go-loop]]
            [clojure.pprint :refer [pprint]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.client.ai :as ai]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.engine.transport.websocket.system :as engine-websocket-system]
            [me.moocar.ftb500.client.transport.websocket.system :as client-websocket-system]
            [me.moocar.ftb500.schema :as schema]
            [me.moocar.ftb500.score :as score]
            [me.moocar.log :as log]))

(def default-timeout 3000)

(def num-players 4)

(def ai-players 4)


(defn new-ai-client
  [config]
  (component/start (client-websocket-system/new-system config)))

(defn <!!all [s]
  (loop [[results ports] [[] (doall s)]]
    (if (= (count results)
           (count s))
      results
      (let [[v port] (async/alts!! ports)]
        (if (instance? Throwable v)
          (throw v)
          (recur [(conj results v)
                  (remove #(= port %) ports)]))))))

(defn clients-thread [clients]
  (async/thread
    (try
      (let [response (<!! (client/send! (first clients) :add-game {:num-players num-players}))]
        (let [game-id (:game/id (second response))]
          (println "game-id" game-id)
          (<!!all (map #(ai/start-playing % game-id) clients))))
      (catch Throwable t t))))

(defn stop-clients
  [clients]
  (->> clients
       (map ai/stop)
       (async/merge)
       (async/take (count clients))
       (async/into [])))

(defn start-and-shutdown-clients
  [clients engine-log]
  (async/thread
    (try
     (let [clients (<!!all (map ai/start clients))
           timeout (async/timeout default-timeout)
           [clients port] (async/alts!! [(clients-thread clients) timeout])]

       (if-not (or (= timeout port)
                   (instance? Throwable clients))
         (do (log/log engine-log "Shutting down clients")
             (let [timeout (async/timeout 2000)
                   [clients port] (async/alts!! [(stop-clients clients) timeout])]

               (doseq [client clients]
                 (component/stop client))
               (if (= timeout port)
                 (log/log engine-log "Failed to shutdown all clients")
                 (do (log/log engine-log "Successfully shutdown all clients")
                     clients))))
         clients))
     (catch Throwable t t))))

(defn print-client-log
  [client]
  (go-try
   (loop []
     (if-let [msg (<? (:output-ch (:log client)))]
       (do (println msg)
           (recur))
       true))))

(defn print-out-client [client]
  (let [timeout (async/timeout default-timeout)
        [v port] (async/alts!! [(print-client-log client) timeout])]
    (if (= timeout port)
      (println "Finished client logs. Timed out = " (= timeout port))
      (if (instance? Throwable v)
        (throw v)))))

(defn print-out-client-logs [clients]
  (println)
  (println "=========== CLIENTS ===========")
  (doseq [client clients]
    (println)
    (println "-------------------------------")
    (print-out-client client)))

(defn new-system [config]
  (let [log (component/start (log/new-logger config))
        engine (component/start (engine-websocket-system/new-system config))
        clients (repeatedly ai-players #(ai/new-client-ai (new-ai-client config)))]
    {:engine engine
     :clients clients
     :log log}))

(defn start-system
  [{:keys [engine clients log] :as system}]
  (let [timeout (async/timeout default-timeout)
        [v port] (async/alts!! [(start-and-shutdown-clients clients log) timeout])]
    (if (instance? Throwable v)
      (do (log/log log v)
          (throw v))
      (if (= timeout port)
        (do
          (log/log log "Shutting down engine after bad timeout")
          (component/stop engine))
        (let [clients v]
          (log/log log "Clients shut down successfully")
          (log/log log "Score")
          (pprint (score/summary (:game (first clients))))))))
  system)

(defn stop-system [system]
  (update system :engine component/stop))

(defn play
  []
  (let [system (new-system (read-string (slurp "local_config.edn")))
        {:keys [engine clients log]} system]
    (try
      (-> system
          start-system
          stop-system)
      (print-out-client-logs clients)
      (finally
        (log/log log "Shutting down engine")
        (component/stop engine)))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.autoplay/play))
