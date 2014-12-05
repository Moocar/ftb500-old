(ns me.moocar.ftb500.engine.websocket
  (:require [clojure.core.async :as async :refer [go go-loop <! >!!]]
            [cognitect.transit :as transit]
            [com.stuartsierra.component :as component]
            [me.moocar.jetty.websocket :as websocket])
  (:import (java.nio ByteBuffer)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (org.eclipse.jetty.server Server ServerConnector)
           (org.eclipse.jetty.websocket.api WebSocketListener Session WriteCallback)
           (org.eclipse.jetty.websocket.client WebSocketClient)
           (org.eclipse.jetty.websocket.server WebSocketHandler)
           (org.eclipse.jetty.websocket.servlet WebSocketCreator)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Java Boilerplate

(defn- websocket-handler 
  "WebSocketHandler that creates creator. Boilerplate"
  [creator]
  (proxy [WebSocketHandler] []
    (configure [factory]
      (.setCreator factory creator))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Websocket connections

(defn- websocket-creator 
  "Creates a WebSocketCreator that when a websocket is opened, waits
  for a connection, and then passes all requests to `af`. af should be
  an async function of 2 arguments, the first a vector of [session
  bytes] and the second a channel to put to (ignored). Returns a
  WebSocketListener"
  [handler]
  (reify WebSocketCreator
    (createWebSocket [this request response]
      (let [connect-ch (async/chan 2)
            request-ch (async/chan 1024)
            read-ch (async/chan 1024)
            write-ch (async/chan 1024)
            error-ch (async/chan 1024)
            connection {:connect-ch connect-ch
                        :request-ch request-ch
                        :read-ch read-ch
                        :write-ch write-ch
                        :error-ch error-ch}]
        (websocket/connection-lifecycle connection)
        (async/pipeline-async 1 write-ch handler request-ch)
        (websocket/listener connection)))))

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

(defn transit-bytes->clj
  [bytes]
  (let [reader (transit/reader (ByteArrayInputStream. bytes) :json)]
    (transit/read reader)))

(defrecord TransitAsyncHandler [app-handler]
  component/Lifecycle
  (start [this]
    (assoc this 
      :af (fn [[session bytes] out-ch]
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
            (println "request:" payload)
            (async/put! out-ch payload)
            (async/put! out-ch payload)
            (async/close! out-ch))))
  (stop [this]
    this))

(defn new-app-handler []
  (map->AppHandler {}))
