(ns me.moocar.jetty.websocket
  (:require [clojure.core.async :as async :refer [go go-loop <! >!!]])
  (:import (java.nio ByteBuffer)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (org.eclipse.jetty.websocket.api WebSocketListener Session WriteCallback)))

(def ^:const request-flag
  "Byte flag placed at the beginning of a packet to indicate the next
  8 bytes are the request-id and that the sender of the packet expects
  to receive a response (with the response flag)"
  (byte 1))

(def ^:const response-flag
  "Byte flag placed at the begninning of a packet to indicate that
  this is a response packet for the request-id in the next 8 bytes"
  (byte 0))

(def ^:const no-request-flag
  "Byte flag placed at the beginning of a packet to indicate that this
  is a request that does not expect a response and therefore the
  request-id is not present (data begins at position 1)"
  (byte -1))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Sending

(defn- write-callback
  "Returns a WriteCallback that closes response-ch upon success, or
  puts cause if failed"
  [response-ch]
  (reify WriteCallback
    (writeSuccess [this]
      (async/close! response-ch))
    (writeFailed [this cause]
      (async/put! response-ch cause))))

(defn send-bytes!
  "Sends bytes to remote-endpoint asynchronously and returns a channel
  that will close once successful or have an exception put onto it in
  the case of an error"
  [remote-endpoint byte-buffer]
  {:pre [remote-endpoint byte-buffer]}
  (println "send bytes!" byte-buffer)
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

(defn make-connection-map
  []
  {:connect-ch (async/chan 2)
   :request-ch (async/chan 1024)
   :read-ch (async/chan 1024)
   :write-ch (async/chan 1024)
   :error-ch (async/chan 1024)
   :response-chans-atom (atom {})
   :request-id-seq-atom (atom 0)})

(defn listener
  "Returns a websocket listener that does nothing but put connections,
  reads or errors into the respective channels"
  [{:keys [connect-ch read-ch error-ch] :as conn}]
  (reify WebSocketListener
    (onWebSocketConnect [this session]
      (async/put! connect-ch session))
    (onWebSocketText [this message]
      (throw (UnsupportedOperationException. "Text not supported")))
    (onWebSocketBinary [this bytes offset len]
      (async/put! read-ch [bytes offset len]))
    (onWebSocketError [this cause]
      (println "on server error" cause)
      (async/put! error-ch cause))
    (onWebSocketClose [this status-code reason]
      (async/put! connect-ch [status-code reason]))))

(defn send-off!
  [{:keys [write-ch] :as conn}
   body-bytes]
  (let [buf (ByteBuffer/allocate (inc (alength body-bytes)))]
    (.put buf no-request-flag)
    (.put buf body-bytes)
    (async/put! write-ch buf)))

(defn add-response-ch
  [{:keys [response-chans-atom] :as conn}
   request-id
   response-ch]
  (swap! response-chans-atom assoc request-id response-ch)
  (async/take! (async/timeout 30000)
               (fn [_]
                 (async/close! response-ch)
                 (swap! response-chans-atom dissoc request-id))))

(defn send!
  [{:keys [write-ch request-id-seq-atom] :as conn}
   body-bytes
   response-ch]
  (println "sending")
  (let [request-id (swap! request-id-seq-atom inc)
        body-size (alength body-bytes)
        request-id-size (+ 8 (/ (Long/SIZE) 8))
        buffer-size (+ 1 request-id-size body-size)
        buf (ByteBuffer/allocate buffer-size)]
    (.put buf request-flag)
    (.putLong buf request-id)
    (.put buf body-bytes)
    (.rewind buf)
    (add-response-ch conn request-id response-ch)
    (println "added to response ch" (:response-chans-atom conn) (get @(:response-chans-atom conn) 1))

    (async/put! write-ch buf)
    response-ch))

(defn make-response-cb
  [{:keys [write-ch] :as conn}
   request-id]
  (fn [body-bytes]
    (println "in callback" body-bytes)
    (let [buf (ByteBuffer/allocate (+ 1 8 (alength body-bytes)))]
      (.put buf response-flag)
      (.putLong buf request-id)
      (.put buf body-bytes)
      (.rewind buf)
      (async/put! write-ch buf))))

(defn response-cb
  [{:keys [response-bytes response-cb] :as request}]
  (response-cb response-bytes))

(defn handle-read
  [{:keys [read-ch request-ch response-chans-atom] :as conn}
   [bytes offset len]]
  (println "byte length" (alength bytes))
  (println "in read. response chans are" response-chans-atom)
  (let [buf (ByteBuffer/wrap bytes offset len)
        packet-type (.get buf)
        request-id (when-not (= no-request-flag packet-type)
                     (.getLong buf))
        body-bytes [bytes (+ offset (.position buf)) (- len (.position buf))]
        to-ch (if (= response-flag packet-type)
                (get @response-chans-atom request-id)
                request-ch)
        request (cond-> {:conn conn
                         :body-bytes body-bytes}
                        (= packet-type request-flag)
                        (assoc :response-cb (make-response-cb conn request-id)))]
    (async/put! to-ch request)))

(defn connection-lifecycle
  "Starts a go loop that first waits for a connection on connect-ch,
  then loops waiting for incoming binary packets. When a packet is
  received, puts [session bytes] onto read-ch. Sends any byte-buffers
  in write-ch to the client. A second message put onto connect-ch is
  assumed to be a close signal, which ends the loop"
  [{:keys [connect-ch read-ch write-ch] :as conn}]
  (go
    (when-let [session (<! connect-ch)]
      (go-loop []
        (async/alt!

          read-ch
          ([v]
             (println "read" v)
             (try
               (handle-read conn v)
               (catch Throwable t
                 (println "error handling read" v)
                 (.printStackTrace t)))
             (recur))

          write-ch
          ([buf]
             (println "writing" buf)
             (send-bytes! (.getRemote session) buf)
             (recur))
          
          connect-ch
          ([[status-code reason]]
             (println "closing because" status-code reason)
             (async/close! connect-ch)))))))
