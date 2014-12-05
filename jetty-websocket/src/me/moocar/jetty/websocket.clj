(ns me.moocar.jetty.websocket
  (:require [clojure.core.async :as async :refer [go go-loop <! >!!]])
  (:import (java.nio ByteBuffer)
           (java.io ByteArrayInputStream ByteArrayOutputStream)
           (org.eclipse.jetty.websocket.api WebSocketListener Session WriteCallback)))

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

(defn send!
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

(defn connection-lifecycle
  "Starts a go loop that first waits for a connection on connect-ch,
  then loops waiting for incoming binary packets. When a packet is
  received, puts [session bytes] onto read-ch. Sends any byte-buffers
  in write-ch to the client. A second message put onto connect-ch is
  assumed to be a close signal, which ends the loop"
  [{:keys [connect-ch read-ch write-ch request-ch] :as conn}]
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
             (send! (.getRemote session) byte-buffer)
             (recur))
          
          connect-ch
          ([[status-code reason]]
             (println "closing because" status-code reason)
             (async/close! connect-ch)))))))
