(ns me.moocar.ftb500.client.sh.play
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component] 
            [me.moocar.ftb500.engine.http.system :as http-system]
            [me.moocar.ftb500.engine.http :as http]
            [me.moocar.ftb500.client.transport :as client-transport]
            [me.moocar.ftb500.client.sh.system :as sh-system]
            [me.moocar.ftb500.engine.transport.jetty9-websocket :as jetty9-websocket]))

(defn go []
  (let [config (read-string (slurp "../local_config.edn")) 
        http-system (component/start (http-system/new-system config))]
    (try
      (let [client-system (component/start (sh-system/new-system config))]
        (try
          (client-transport/send! (:client-transport client-system) "Hi there")
          (finally
            (component/stop client-system))))
      (finally
        (component/stop http-system)))))

(defn reset []
  (refresh :after 'me.moocar.ftb500.client.sh.play/go))
