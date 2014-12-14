(ns me.moocar.ftb500.engine.transport.websocket
  (:require [com.stuartsierra.component :as component] 
            [cognitect.transit :as transit]
            [me.moocar.jetty.websocket.server :as websocket-server])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Transit

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server

(defn wrap-response-cb
  [{:keys [response-cb] :as request}]
  (assoc request
    :response-cb (fn [body]
                   (let [bytes (write-bytes body)]
                     (response-cb [bytes 0 (count bytes)])))))

(defn wrap-handler-xf
  [xf]
  (comp (map request-read-bytes)
        (map wrap-response-cb)
        xf))

(defrecord WebSocketServer [websocket-server handler-xf]
  component/Lifecycle
  (start [this]
    (assoc this
      :websocket-server (websocket-server/start 
                         (assoc websocket-server 
                           :handler-xf (wrap-handler-xf handler-xf)))))
  (stop [this]
    (assoc this 
      :websocket-server (websocket-server/stop websocket-server))))

(defn new-websocket-server
  [config]
  (component/using
    (map->WebSocketServer 
     {:websocket-server (websocket-server/new-websocket-server 
                         (get-in config [:engine :websocket]) config)})
    {:handler-xf :engine-handler-xf}))
