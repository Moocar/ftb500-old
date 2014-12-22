(ns me.moocar.ftb500.engine.transport.websocket
  (:require [com.stuartsierra.component :as component]
            [cognitect.transit :as transit]
            [me.moocar.comms-async :as comms]
            [me.moocar.jetty.websocket :as websocket]
            [me.moocar.jetty.websocket.server :as websocket-server])
  (:import (java.io ByteArrayOutputStream ByteArrayInputStream)))

(defn clj->bytes [clj]
  (let [out (java.io.ByteArrayOutputStream.)
        writer (transit/writer out :json)]
    (transit/write writer clj)
    (.toByteArray out)))

(defn bytes->clj [bytes offset len]
  (let [reader (transit/reader (java.io.ByteArrayInputStream. bytes offset len) :json)]
    (transit/read reader)))

(defn new-transit-conn
  [request]
  (let [send-xf (comp (map (fn [m] (println "server-send-ch" m) m))
                      (map (comms/custom-send bytes->clj clj->bytes)))]
    (websocket/make-connection-map send-xf)))

(defrecord WebSocketServer [websocket-server clients-atom handler]
  component/Lifecycle
  (start [this]
    (let [websocket-server (websocket-server/start
                            (assoc websocket-server
                                   :new-conn-f new-transit-conn
                                   :handler-xf (comp (map (comms/custom-request bytes->clj))
                                                     (:xf handler)
                                                     (keep (comms/custom-response clj->bytes)))))]
      (assoc this
             :websocket-server websocket-server)))
  (stop [this]
    (let [stopped (websocket-server/stop websocket-server)]
      (assoc this 
             :websocket-server stopped))))

(defn new-websocket-server
  [config]
  (component/using
    (map->WebSocketServer
     {:websocket-server (websocket-server/new-websocket-server
                         (get-in config [:engine :websocket :server]))})
    {:handler :engine-handler}))
