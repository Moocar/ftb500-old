(ns me.moocar.async
  (:require [clojure.core.async :as async]))

(defn throw-err [e]
  (when (instance? Throwable e)
    (throw e))
  e)
 
(defmacro <? [ch]
  `(throw-err (async/<! ~ch)))
