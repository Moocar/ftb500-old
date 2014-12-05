(ns me.moocar.ftb500.engine.websocket
  "A WebSocketServlet for Jetty 9 that offloads WebSocket
  communication to core.async channels."
  (:require [clojure.core.async :as async :refer [go go-loop <! >!!]]
            [clojure.string  :as string]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component])
  (:import (javax.servlet.http HttpServletRequest)
           (java.nio ByteBuffer)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (org.eclipse.jetty.server Server ServerConnector)
           (org.eclipse.jetty.websocket.api WebSocketAdapter Session WriteCallback)
           (org.eclipse.jetty.websocket.client WebSocketClient)
           (org.eclipse.jetty.websocket.server WebSocketHandler)
           (org.eclipse.jetty.websocket.servlet WebSocketCreator 
                                                WebSocketServletFactory
                                                WebSocketServlet)))

(defn write-callback
  "Returns a WriteCallback that closes response-ch upon success, or
  puts cause if failed"
  [response-ch]
  (reify WriteCallback
    (writeSuccess [this]
      (println "succeeded")
      (async/close! response-ch))
    (writeFailed [this cause]
      (println "failed!" cause)
      (async/put! response-ch cause))))

(defn send-byte-buffer 
  "Sends bytes to remote-endpoint asynchronously and returns a channel
  that will close once successful or have an exception put onto it in
  the case of an error"
  [remote-endpoint byte-buffer]
  {:pre [remote-endpoint byte-buffer]}
  (let [response-ch (async/chan)]
    (try
      (.sendBytes remote-endpoint 
                  byte-buffer
                  (write-callback response-ch))
      (catch Throwable t
        (println "caught an exc" t)
        (async/put! response-ch t))
      (finally
        (async/close! response-ch)))
    response-ch))

(defn- connection-lifecycle
  "Starts a go loop that first waits for a connection on connect-ch,
  then loops waiting for incoing binary packets. When a packet is
  received, puts [session bytes] onto read-ch. A second message put
  onto connect-ch is assumed to be a close signal, which ends the
  loop"
  [connect-ch read-ch request-ch]
  (go
    (when-let [session (<! connect-ch)]
      (go-loop []
        (async/alt! 
          
          read-ch
          ([[bytes]] 
             (async/put! request-ch [session bytes])
             (recur))
          
          connect-ch
          ([[status-code reason]]
             (println "closing because" status-code reason)
             (async/close! connect-ch)))))))

(defn- websocket-adapter 
  "Returns a websocket adapter that does nothing but put connections,
  reads or errors into the respective channels"
  [connect-ch read-ch error-ch]
  (proxy [WebSocketAdapter] []
    (onWebSocketConnect [session]
      (async/put! connect-ch session))
    (onWebSocketText [message]
      (throw (UnsupportedOperationException. "Text not supported")))
    (onWebSocketBinary [bytes offset len]
      (async/put! read-ch [bytes offset len]))
    (onWebSocketError [cause]
      (println "on server error" cause)
      (async/put! error-ch cause))
    (onWebSocketClose [status-code reason]
      (async/put! connect-ch [status-code reason]))))

(defn- websocket-creator 
  "Creates a WebSocketCreator that when a websocket is opened, waits
  for a connection, and then passes all requests to `af`. af should be
  an async function of 2 arguments, the first a vector of [session
  bytes] and the second a channel to put to (ignored). Returns a
  WebSocketAdapter"
  [af]
  (reify WebSocketCreator
    (createWebSocket [this request response]
      (let [connect-ch (async/chan 2)
            request-ch (async/chan 1024)
            read-ch (async/chan 1024)
            error-ch (async/chan 1024)
            to-ch (async/chan 1024)]
        (connection-lifecycle connect-ch read-ch request-ch)
        (async/pipeline-async 1 to-ch af request-ch)
        (websocket-adapter connect-ch read-ch error-ch)))))

(defn- websocket-handler 
  "WebSocketHandler that creates WebSocketCreators"
  [af]
  (proxy [WebSocketHandler] []
    (configure [factory]
      (.setCreator factory (websocket-creator af)))))

(defrecord WebSocketServer [port server async-handler]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [server (Server.)
            connector (doto (ServerConnector. server)
                        (.setPort port))
            ws-handler (websocket-handler (:af async-handler))]
        (.addConnector server connector)
        (.setHandler server ws-handler)
        (.start server)
        (assoc this
          :server server))))
  (stop [this]
    (if server
      (do 
        (.stop server)
        (assoc this :server nil))
      this)))

(defn new-websocket-server [config]
  (component/using
    (map->WebSocketServer (get-in config [:engine :http :server]))
    {:async-handler :transit-async-handler}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Async Handler

(defn transit-byte-buffer
  [packet]
  (let [out (ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer packet)
    (ByteBuffer/wrap (.toByteArray out))))

(defn send! 
  [session payload]
  (let [remote (.getRemote session)]
    (send-byte-buffer remote (transit-byte-buffer payload))))

(defn echo
  [[session payload]]
  (println "received" payload)
  (send! session payload))

(defn transit-bytes->clj
  [bytes]
  (let [reader (transit/reader (ByteArrayInputStream. bytes) :json)]
    (transit/read reader)))

(defrecord TransitAsyncHandler []
  component/Lifecycle
  (start [this]
    (assoc this 
      :af (fn [[session bytes] input-value]
            (echo [session (transit-bytes->clj bytes)]))))
  (stop [this]
    this))

(defn new-transit-async-handler []
  (map->TransitAsyncHandler {}))
