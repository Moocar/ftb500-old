(ns me.moocar.ftb500.client.sh.play
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [clojure.core.async :as async :refer [go <! <!! alts!!]]
            [com.stuartsierra.component :as component] 
            [me.moocar.ftb500.client.transport :as client-transport]
            [me.moocar.ftb500.client.sh.system :as sh-system]
            [me.moocar.ftb500.client.transport.jetty-ws :as jetty-ws]
            [me.moocar.ftb500.engine.websocket :as websocket-server]
            [me.moocar.jetty.websocket.transit :as ws-transit]
            [me.moocar.ftb500.engine.websocket.system :as websocket-system]))

(defn play []
  (let [config (read-string (slurp "../local_config.edn")) 
        http-system (component/start (websocket-system/new-system config))]
    (try
      (let [client-system (component/start (sh-system/new-system config))]
        (try
          (let [result (first (alts!! [(ws-transit/send! (:conn (:client-transport client-system))
                                                       {:route :abc})
                                       (async/timeout 1000)]))]
            (println "got result from server" result)
            (Thread/sleep 100)
            (if (instance? Throwable result)
              (throw result)))
          (finally
            (component/stop client-system))))
      (finally
        (component/stop http-system)))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.sh.play/play))
