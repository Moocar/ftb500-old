(ns me.moocar.ftb500.client.transport.websocket
  (:require [clojure.core.async :as async] 
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.listener :as client-listener]
            [me.moocar.jetty.websocket.client :as websocket-client]
            [me.moocar.lang :refer [uuid]])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

(defn write-bytes
  "Serializes a clojure datastructure using transit :json format.
  Returns bytes"
  [thing]
  (let [out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer thing)
    (.toByteArray out)))

(defn read-bytes
  "Deserializes bytes using transit :json and returns the clojure
  datastructure"
  ([bytes]
     (read-bytes bytes 0 (alength bytes)))
  ([bytes offset len]
     (let [reader (transit/reader (ByteArrayInputStream. bytes offset len) :json)]
       (transit/read reader))))

(defn request-read-bytes
  [{:keys [body-bytes] :as request}]
  (let [[bytes offset len] body-bytes]
    (assoc request
      :body (read-bytes bytes offset len))))

(defn transitize
  [[body response-ch]]
  [(write-bytes body)
   (when response-ch
     (let [transit-ch (async/chan 1 (comp (map request-read-bytes)
                                          (map :body)))]
       (async/pipe transit-ch response-ch)
       transit-ch))])

(defrecord WebSocketClient [websocket-client listener send-ch client-id log]

  component/Lifecycle
  (start [this]
    (let [request-ch (async/chan 1024 (map request-read-bytes))
          listener (client-listener/start
                    (client-listener/new-client-listener log request-ch client-id))
          websocket-client (websocket-client/start 
                            (assoc websocket-client
                              :request-ch request-ch
                              :send-ch send-ch))]
      (assoc this
        :websocket-client websocket-client
        :request-ch request-ch
        :listener listener)))
  (stop [this]
    (client-listener/stop listener)
    (assoc this
      :websocket-client (websocket-client/stop websocket-client)
      :listener nil)))

(defn new-websocket-client
  [config]
  (component/using
    (map->WebSocketClient
     {:websocket-client (websocket-client/new-websocket-client 
                         (get-in config [:engine :websocket]))
      :send-ch (async/chan 1 (map transitize))
      :client-id (java.util.UUID/randomUUID)})
    [:log]))



