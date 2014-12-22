(ns me.moocar.ftb500.client.transport.websocket
  (:require [clojure.core.async :as async] 
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [me.moocar.comms-async :as comms]
            [me.moocar.jetty.websocket :as websocket]
            [me.moocar.ftb500.client.listener :as client-listener]
            [me.moocar.jetty.websocket.client :as websocket-client]
            [me.moocar.lang :refer [uuid]])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

(defn clj->bytes [clj]
  (let [out (java.io.ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer clj)
    (.toByteArray out)))

(defn bytes->clj [bytes offset len]
  (let [reader (transit/reader (java.io.ByteArrayInputStream. bytes offset len) :json)]
    (transit/read reader)))

(defn new-transit-conn []
  (websocket/make-connection-map (map (comms/custom-send bytes->clj clj->bytes))))

(defrecord WebSocketClient [websocket-client listener send-ch client-id log]

  component/Lifecycle
  (start [this]
    (let [request-ch (async/chan 1024 (comp (map (comms/custom-request bytes->clj))
                                            (map :body)))
          listener (client-listener/start
                    (client-listener/new-client-listener log request-ch client-id))
          websocket-client (websocket-client/start 
                            (assoc websocket-client
                              :request-ch request-ch
                              :new-conn-f new-transit-conn))]
      (-> this
          (merge websocket-client)
          (assoc :listener listener))))
  (stop [this]
    (client-listener/stop listener)
    (-> this
        websocket-client/stop
        (assoc :listener nil))))

(defn new-websocket-client
  [config]
  (component/using
    (map->WebSocketClient
     {:websocket-client (websocket-client/new-websocket-client
                         (get-in config [:engine :websocket :server]))
      :client-id (uuid)})
    [:log]))



