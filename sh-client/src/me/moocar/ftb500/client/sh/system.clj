(ns me.moocar.ftb500.client.sh.system
  (:require [clojure.core.async :as async :refer [<!! thread]]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.sh :as sh-client]
            [me.moocar.ftb500.client.transport.websocket.system :as websocket-system]
            [me.moocar.log :as log]))

(defn into-file
  "Creates a reducing function for the given file that will simply
  print each item on a new line"
  [^String filename]
  (fn
    ([] (jio/writer filename))
    ([^java.io.Writer os]
     (.close os)
     nil)
    ([^java.io.Writer os item]
     (binding [*out* os]
       (println item)
       os))))

(defn reducible-chan
  "Returns a Reducible thing from a channel. When reduced, will invoke
  reducing function with successive values read from ch with <!! until
  ch is closed."
  [ch]
  (reify clojure.core.protocols/CollReduce
    (coll-reduce [this step init]
      (loop [result init]
        (if (reduced? result)
          @result
          (if-some [input (<!! ch)]
            (recur (step result input))
            result))))))

(defn sh-system [console config]
  (let [log-ch (async/chan 1 (keep log/format-log))]
    (thread
      (transduce
       (completing identity)
       (into-file "/tmp/sh-client.log")
       (reducible-chan log-ch)))
    (component/system-map
     :sh-client (sh-client/new-sh-client console config)
     :log-ch log-ch)))

(defn new-system [console config]
  (merge (websocket-system/new-system config)
         (sh-system console config)))
