(ns me.moocar.ftb500.websocket-server-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [<!!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.transport.websocket :as websocket-server]
            [me.moocar.ftb500.client.websocket :as websocket-client]
            [me.moocar.log :as log]
            [me.moocar.transport :as transport]))

(defn echo-handler
  []
  (keep (fn [{:keys [response-cb body] :as request}]
          (response-cb body)
          nil)))

(defn new-engine-system
  [config]
  (component/system-map
   :websocket-server (websocket-server/new-websocket-server config)
   :engine-handler-xf (echo-handler)))

(defn new-client-system
  [config]
  (component/system-map
   :websocket-client (websocket-client/new-websocket-client config)
   :log (log/new-logger config)))

(deftest start-stop-test
  (let [websocket-config {:port 8083
                          :hostname "localhost" 
                          :websockets {:scheme :ws
                                       :path "/ws"}}
        engine-system (-> {:engine {:websocket websocket-config}}
                          new-engine-system
                          component/start)]
    (try
      (let [client-system (-> {:engine {:websocket websocket-config}}
                              new-client-system
                              component/start)
            request {:route :moo/car :body "haha"}
            response-ch (async/chan)]
        (try
          (is (= (<!! (transport/-send! (:websocket-client client-system) request))
                 request))
          
          (finally
            (component/stop client-system))))
      (finally
        (component/stop engine-system)))))
