(ns me.moocar.ftb500.client.transport.jetty-ws
  (:require [clojure.core.async :as async :refer [go <!]]
            [clojure.tools.namespace.repl :refer [refresh refresh-all]]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport :as client-transport])
  (:import (java.net URI)
           (java.io ByteArrayOutputStream)
           (java.nio ByteBuffer)
           (java.util.concurrent TimeUnit)
           (org.eclipse.jetty.websocket.api WebSocketListener Session WriteCallback)
           (org.eclipse.jetty.websocket.client WebSocketClient ClientUpgradeRequest)))

(defn new-web-socket-listener
  []
  (reify WebSocketListener
    (onWebSocketBinary [this payload offset len]
      (println "binary on client"))
    (onWebSocketClose [this status-code reason]
      (println "close on client"))
    (onWebSocketConnect [this session]
      (println "Client has connected!"))
    (onWebSocketError [this throwable]
      (println "Client Error!" throwable))
    (onWebSocketText [this message]
      (println "Client text" message))))

(defn write-callback
  [response-ch]
  (reify WriteCallback
    (writeSuccess [this]
      (println "succeeded")
      (async/close! response-ch))
    (writeFailed [this cause]
      (println "failed!" cause)
      (async/put! response-ch cause))))

(defn send-bytes 
  [remote-endpoint byte-buffer]
    (let [response-ch (async/chan)]
      (try
        (.sendBytes remote-endpoint 
                    byte-buffer
                    (write-callback response-ch))
        (catch Throwable t 
          (println "caught an exc")
          (async/put! response-ch t))
        (finally
          (async/close! response-ch)))
      response-ch))

(defrecord JettyWSClientTransport [hostname port connect-timeout websockets]

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
          _ (.start client)
          session (deref (.connect client listener uri) connect-timeout nil)]
      (when-not session
        (throw (ex-info "Failed to connect"
                        this)))
      (println "session" session)
      (assoc this
        :session session)))
  (stop [this]
    this)

  client-transport/ClientTransport
  (-send! [this msg timeout cb])
  (-send! [this byte-buffer]
    (send-bytes (.getRemote (:session this)) byte-buffer)))

(defn transit-byte-buffer
  [packet]
  (let [out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer packet)
    (ByteBuffer/wrap (.toByteArray out))))

(defn send!
  [{:keys [seq-atom] :as this} 
   body]
  (go
    (let [response-ch (async/chan)
          msg {:seq (swap! seq-atom inc)
               :body body}
          byte-buffer (transit-byte-buffer msg)
          error-ch (client-transport/-send! this byte-buffer)]
      (or (<! error-ch)
          #_(<! response-ch)))))

(defn new-java-ws-client-transport
  [config]
  (let [http-config (get-in config [:engine :http :server])]
    (map->JettyWSClientTransport (merge http-config
                                        {:seq-atom (atom 0)}))))
