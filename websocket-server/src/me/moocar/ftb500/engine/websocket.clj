(ns me.moocar.ftb500.engine.websocket
  (:require [clojure.core.async :as async :refer [go go-loop <! >!!]]
            [me.moocar.jetty.websocket.transit :as ws-transit]
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

(defn create-websocket
  [handler-xf]
  (fn [this request response]
    (let [conn (websocket/make-connection-map)]
      (websocket/start-connection conn handler-xf))))

(defn- websocket-creator 
  "Creates a WebSocketCreator that when a websocket is opened, waits
  for a connection, and then passes all requests to `af`. af should be
  an async function of 2 arguments, the first a vector of [session
  bytes] and the second a channel to put to (ignored). Returns a
  WebSocketListener"
  [create-websocket-f]
  (reify WebSocketCreator
    (createWebSocket [this request response]
      (create-websocket-f this request response))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Component

(defrecord WebSocketServer [port handler-xf server]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [server (Server.)
            connector (doto (ServerConnector. server)
                        (.setPort port))
            create-websocket-f (create-websocket handler-xf)
            creator (websocket-creator create-websocket-f)
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
    [:handler-xf]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Handler

(defn echo
  [{:keys [body conn] :as request}]
  (println "request:" body)
  (ws-transit/send-off! conn "This is my broadcast. Muthafucker!!!!!!!!!")
  (async/take! (ws-transit/send! conn {:route :answer-it})
               #(println "server got response from client" %))
  (assoc request :response body))

(defn make-handler-xf []
  (comp (map ws-transit/request-read-bytes)
        (keep echo)
        (keep ws-transit/response-write-byte-buffer)
        (keep websocket/response-cb)
        (keep (constantly nil))))
