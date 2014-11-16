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
