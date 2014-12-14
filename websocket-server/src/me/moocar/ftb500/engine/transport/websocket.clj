(ns me.moocar.ftb500.engine.transport.websocket
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [cognitect.transit :as transit]
            [me.moocar.lang :refer [uuid? uuid]]
            [me.moocar.jetty.websocket :as websocket]
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

(defn transitize []
  (comp (map request-read-bytes)
        (map wrap-response-cb)))

(defn transitize-send
  [[body response-ch]]
  [(write-bytes body)
   (when response-ch
     (let [transit-ch (async/chan 1 (comp (map request-read-bytes)
                                          (map :body)))]
       (async/pipe transit-ch response-ch)
       transit-ch))])

(defn make-conn
  [clients-atom]
  (fn [request]
    (println "in make conn" (.getLocalAddress request))
    (let [send-ch (async/chan 1 (map transitize-send))
          client-id (uuid)
          conn (assoc (websocket/make-connection-map send-ch)
                 :client/id client-id)]
      (swap! clients-atom assoc client-id conn)
      conn)))

(defn get-client-conn
  [{:keys [clients-atom] :as request}
   client-id]
  (get @clients-atom client-id))

(defrecord WebSocketServer [websocket-server clients-atom handler-xf]
  component/Lifecycle
  (start [this]
    (assoc this
      :websocket-server (websocket-server/start 
                         (assoc websocket-server 
                           :new-conn-f (make-conn clients-atom)
                           :handler-xf (comp (transitize)
                                             handler-xf)))))
  (stop [this]
    (assoc this 
      :websocket-server (websocket-server/stop websocket-server))))

(defn new-websocket-server
  [config]
  (component/using
    (map->WebSocketServer 
     {:websocket-server (websocket-server/new-websocket-server 
                         (get-in config [:engine :websocket]))
      :clients-atom (atom {})})
    {:handler-xf :engine-handler-xf}))
