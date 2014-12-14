(ns me.moocar.ftb500.websocket-server-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [<!!]]
            [com.stuartsierra.component :as component]
            [me.moocar.async :as moo-async]
            [me.moocar.ftb500.engine.transport.websocket :as websocket-server]
            [me.moocar.ftb500.client.transport.websocket :as websocket-client]
            [me.moocar.log :as log]
            [me.moocar.transport :as transport]))

(defn echo-handler
  [client-id-ch]
  (keep (fn [{:keys [response-cb body] :as request}]
          (println "handling the request" request)
          (async/put! client-id-ch (:client/id (:conn request)))
          (response-cb body)
          nil)))

(defn new-engine-system
  [config client-id-ch]
  (component/system-map
   :websocket-server (websocket-server/new-websocket-server config)
   :engine-handler-xf (echo-handler client-id-ch)))

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
        client-id-ch (async/chan 1)
        engine-system (-> {:engine {:websocket websocket-config}}
                          (new-engine-system client-id-ch)
                          component/start)
        websocket-server (:websocket-server engine-system)]
    (try
      (let [client-system (-> {:engine {:websocket websocket-config}}
                              new-client-system
                              component/start)
            client (:websocket-client client-system)
            client-recv-ch (:request-ch client)
            request {:route :moo/car :body "haha"}]
        (try
          (let [response (<!! (moo-async/request (:send-ch client) 
                                                 request))]
            (is (= response request)))
          (let [client-id (<!! client-id-ch)
                conn (websocket-server/get-client-conn websocket-server client-id)
                send-off-msg "Booya!!!"])
          (finally
            (component/stop client-system))))
      (finally
        (component/stop engine-system)))))
