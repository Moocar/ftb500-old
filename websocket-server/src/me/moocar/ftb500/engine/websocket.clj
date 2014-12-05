(ns me.moocar.ftb500.engine.websocket
  (:require [clojure.core.async :as async :refer [go go-loop <! >!!]]
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Java Boilerplate

(defn- websocket-handler 
  "WebSocketHandler that creates creator. Boilerplate"
  [creator]
  (proxy [WebSocketHandler] []
    (configure [factory]
      (.setCreator factory creator))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Sending

(defn- write-callback
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Websocket connections

(defn- connection-lifecycle
  "Starts a go loop that first waits for a connection on connect-ch,
  then loops waiting for incoing binary packets. When a packet is
  received, puts [session bytes] onto read-ch. Sends any byte-buffers
  in write-ch to the client. A second message put onto connect-ch is
  assumed to be a close signal, which ends the loop"
  [connect-ch read-ch write-ch request-ch]
  (go
    (when-let [session (<! connect-ch)]
      (go-loop []
        (async/alt! 
          
          read-ch
          ([[bytes]] 
             (async/put! request-ch [session bytes])
             (recur))

          write-ch
          ([byte-buffer]
             (send-byte-buffer (.getRemote session) byte-buffer))
          
          connect-ch
          ([[status-code reason]]
             (println "closing because" status-code reason)
             (async/close! connect-ch)))))))

(defn- websocket-creator 
  "Creates a WebSocketCreator that when a websocket is opened, waits
  for a connection, and then passes all requests to `af`. af should be
  an async function of 2 arguments, the first a vector of [session
  bytes] and the second a channel to put to (ignored). Returns a
  WebSocketAdapter"
  [handler]
  (reify WebSocketCreator
    (createWebSocket [this request response]
      (let [connect-ch (async/chan 2)
            request-ch (async/chan 1024)
            read-ch (async/chan 1024)
            write-ch (async/chan 1024)
            error-ch (async/chan 1024)]
        (connection-lifecycle connect-ch read-ch write-ch request-ch)
        (async/pipeline-async 1 write-ch handler request-ch)
        (websocket-adapter connect-ch read-ch error-ch)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Component

(defrecord WebSocketServer [port server async-handler]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [server (Server.)
            connector (doto (ServerConnector. server)
                        (.setPort port))
            creator (websocket-creator (:af async-handler))
            ws-handler (websocket-handler creator)]
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

(defn transit-bytes->clj
  [bytes]
  (let [reader (transit/reader (ByteArrayInputStream. bytes) :json)]
    (transit/read reader)))

(defrecord TransitAsyncHandler [app-handler]
  component/Lifecycle
  (start [this]
    (assoc this 
      :af (fn [[session bytes] out-ch]
            (println "got bytes" bytes)
            (let [transit-ch (async/chan 1 (map transit-byte-buffer))]
              (async/pipe transit-ch out-ch)
              ((:af app-handler) [session (transit-bytes->clj bytes)]
               transit-ch)))))
  (stop [this]
    this))

(defn new-transit-async-handler []
  (component/using 
    (map->TransitAsyncHandler {})
    [:app-handler]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; App Hanler

(defrecord AppHandler []
  component/Lifecycle
  (start [this]
    (assoc this
      :af (fn [[session payload] out-ch]
            (async/put! out-ch payload)
            (async/close! out-ch))))
  (stop [this]
    this))

(defn new-app-handler []
  (map->AppHandler {}))
