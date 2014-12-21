(ns me.moocar.async
  (:require [clojure.core.async :as async]))

(defmacro go-try
  [& body]
  `(async/go
     (try
       ~@body
       (catch Throwable t# t#))))

(defn throw-err [e]
  (when (instance? Throwable e)
    (throw e))
  e)

(defmacro <? [ch]
  `(throw-err (async/<! ~ch)))
 
(defmacro <!? [ch]
  `(throw-err (async/<! ~ch)))

(defmacro <!!? [ch]
  `(throw-err (async/<! ~ch)))

(defn send-off!
  [send-ch request]
  (async/put! send-ch [request]))

(defn request
  [send-ch request]
  (let [response-ch (async/chan 1)]
    (async/put! send-ch [request response-ch])
    response-ch))

(defn retry-request 
  [send-ch request]
  (let [response-ch (async/chan 1)
        max-wait-time 10000]
    (async/go-loop [timeout-msecs 1000
                    total-wait-time 0]
      (let [timeout-ch (async/timeout timeout-msecs)]
        (async/put! send-ch [request response-ch])
        (async/alt! response-ch ([v] v)
                    timeout-ch ([_] 
                                  (println "retrying" {:request request
                                                       :timeout timeout-msecs})
                                  (recur (* timeout-msecs 2)
                                         (+ total-wait-time timeout-msecs))))))))
