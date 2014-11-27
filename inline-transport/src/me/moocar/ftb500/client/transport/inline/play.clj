(ns me.moocar.ftb500.client.transport.inline.play
  (:require [clojure.core.async :as async :refer [go <! <!! go-loop]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.ai :as ai]
            [me.moocar.ftb500.client.transport.inline.system :as inline-client-system]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.schema :as schema]
            [me.moocar.ftb500.score :as score]
            [me.moocar.log :as log]))

(defn new-ai-client
  [engine config]
  (component/start (merge engine (inline-client-system/new-system config))))

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
      (let [response (<!! (client/send! (first clients) :add-game {:num-players 4}))]
        (let [game-id (:game/id (second response))]
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
           timeout (async/timeout 3000)
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
  (let [timeout (async/timeout 3000)
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

(defn play
  []
  (let [config (read-string (slurp "../local_config.edn"))
        engine (component/start (engine-system/new-system config))
        clients (repeatedly 4 #(ai/new-client-ai (new-ai-client engine config)))
        log (:log engine)]
    (try
      (let [timeout (async/timeout 3000)
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
              (clojure.pprint/pprint {:score (score/summary (:game (first clients)))})))))
      (finally
        (log/log log "Shutting down engine")
        (component/stop engine)))

    (print-out-client-logs clients)))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.transport.inline.play/play))




