(ns me.moocar.ftb500.client.transport.jetty-ws
  (:require [clojure.core.async :as async :refer [go go-loop <! <!!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport :as client-transport]
            [me.moocar.jetty.websocket :as websocket]
            [me.moocar.jetty.websocket.transit :as ws-transit])
  (:import (java.net URI)
           (java.io ByteArrayOutputStream)
           (java.nio ByteBuffer)
           (java.util.concurrent TimeUnit)
           (org.eclipse.jetty.websocket.api WebSocketListener Session WriteCallback)
           (org.eclipse.jetty.websocket.client WebSocketClient ClientUpgradeRequest)))

(defn- make-uri
  [{:keys [hostname port websockets]}]
  (let [{:keys [scheme path]} websockets
        uri-string (format "%s://%s:%s%s"
                           (name scheme)
                           hostname
                           port
                           path)]
    (URI. uri-string)))

(defrecord JettyWSClientTransport [hostname port connect-timeout websockets handler-xf]

  component/Lifecycle
  (start [this]
    (let [client (WebSocketClient.)
          uri (make-uri this)
          conn (websocket/make-connection-map)
          listener (websocket/listener conn)]
      (websocket/start-connection conn listener handler-xf)
      (.start client)
      (if (deref (.connect client listener uri) 1000 nil)
        (assoc this :conn conn)
        (throw (ex-info "Failed to connect"
                        this)))))
  (stop [this]
    this))

(defn send!
  [client body]
  (println "client send")
  (let [response-ch (async/chan 1 (comp (map ws-transit/request-read-bytes)
                                        (map :body)))]
    (websocket/send! (:conn client) (ws-transit/write-bytes body) response-ch)
    response-ch))

(defn new-java-ws-client-transport
  [config]
  (let [http-config (get-in config [:engine :http :server])]
    (component/using
      (map->JettyWSClientTransport (merge http-config
                                          {:seq-atom (atom 0)}))
      [:handler-xf])))

(defn make-handler-xf
  []
  (comp (map ws-transit/request-read-bytes) 
        (keep #(println "hello" (:body %)))))
