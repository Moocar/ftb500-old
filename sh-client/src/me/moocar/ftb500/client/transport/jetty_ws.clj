(ns me.moocar.ftb500.client.transport.jetty-ws
  (:require [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport :as client-transport])
  (:import (java.net URI)
           (java.util.concurrent TimeUnit)
           (org.eclipse.jetty.websocket.api WebSocketListener Session)
           (org.eclipse.jetty.websocket.client WebSocketClient ClientUpgradeRequest)))

(defn new-web-socket-listener
  []
  (reify WebSocketListener
    (onWebSocketBinary [this payload offset len]
      )
    (onWebSocketClose [this status-code reason]
      )
    (onWebSocketConnect [this session]
      )
    (onWebSocketError [this throwable]

      (println "Error!" throwable))
    (onWebSocketText [this message]
      )))

(defrecord JettyWSClientTransport [hostname port websockets]

  component/Lifecycle
  (start [this]
    (let [client (WebSocketClient.)
          listener (new-web-socket-listener)
          {:keys [scheme path]} websockets
          uri-string (format "%s://%s:%s%s"
                             (name scheme)
                             hostname
                             port
                             path)
          uri (URI. uri-string)
          request (ClientUpgradeRequest.)
          _ (.start client)
          session @(.connect client listener uri request)]
      (assoc this
        :session session)))
  (stop [this]
    this)

  client-transport/ClientTransport
  (-send! [this msg]
    (let [{:keys [route]} msg]
      (assert (keyword? route))
      #_(send-fn [route (dissoc msg :route)])))
  (-send! [this msg timeout-ms callback]
    (let [{:keys [route]} msg]
      (assert (keyword? route))
      #_(send-fn [route (dissoc msg :route)] timeout-ms callback))))

(defn new-java-ws-client-transport
  [config]
  (let [http-config (get-in config [:engine :http :server])]
    (map->JettyWSClientTransport http-config)))
