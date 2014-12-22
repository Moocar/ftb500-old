(ns me.moocar.ftb500.client.sh.play
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.core.async :as async :refer [go <! <!! alts!!]]
            [com.stuartsierra.component :as component] 
            [me.moocar.async :as moo-async :refer [<!!?]]
            [me.moocar.ftb500.client.transport.websocket.system :as client-websocket-system]
            [me.moocar.ftb500.engine.transport.websocket.system :as engine-websocket-system]))

(defn play []
  (let [config (read-string (slurp "local_config.edn")) 
        engine-system (component/start (engine-websocket-system/new-system config))]
    (try
      (let [client-system (component/start (client-websocket-system/new-system config))
            send-ch (:send-ch (:conn (:transport client-system)))]
        (try
          (let [result (<!!? (moo-async/timed-request send-ch {:route :abc}))]
            (println "got result" result))
          #_(let [result (<!! (moo-async/request )) (first (alts!! [(transport/send! (:conn (:client-transport client-system))
                                                        {:route :abc})
                                       (async/timeout 1000)]))]
            (println "got result from server" result)
            (Thread/sleep 100)
            (if (instance? Throwable result)
              (throw result)))
          (finally
            (component/stop client-system))))
      (finally
        (component/stop engine-system)))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.sh.play/play))
