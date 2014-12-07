(ns me.moocar.jetty.websocket.transit
  (:require [clojure.core.async :as async :refer [go go-loop <! >!!]]
            [me.moocar.jetty.websocket :as websocket]
            [cognitect.transit :as transit])
  (:import (java.io ByteArrayInputStream ByteArrayOutputStream)))

(defn write-bytes
  "Serializes a clojure datastructure using transit :json format.
  Returns bytes"
  [thing]
  (println "writing bytes")
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

(defn update-copy
  [map select-key f new-key]
  (assoc map new-key (f (get map select-key))))

(defn request-read-bytes
  [{:keys [body-bytes] :as request}]
  (println "body bytes" body-bytes)
  (let [[bytes offset len] body-bytes]
    (assoc request
      :body (read-bytes bytes offset len))))

(defn response-write-byte-buffer
  [{:keys [response] :as request}]
  (when response
    (update-copy request :response write-bytes :response-bytes)))

(defn send-off!
  [conn body]
  (websocket/send-off! conn (write-bytes body)))

(defn send!
  [conn body]
  (let [response-ch (async/chan 1 (comp (map request-read-bytes)
                                        (map :body)))]
    (websocket/send! conn (write-bytes body) response-ch)
    response-ch))
