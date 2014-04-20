(ns me.moocar.log
  (:require [clojure.core.async :refer [go-loop chan <! put! close!]]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]))

(defrecord Logger [output-ch]
  component/Lifecycle
  (start [this]
    (let [output-ch (chan 1024)]
      (go-loop []
        (when-let [log (<! output-ch)]
          (let [non-ex (dissoc log :ex)]
            (pprint non-ex)
            (when (:ex log)
              (.printStackTrace (:ex log))))
          (recur)))
      (assoc this :output-ch output-ch)))
  (stop [this]
    (close! output-ch)
    (assoc this :output-ch nil)))

(defn new-logger
  [config]
  (map->Logger {}))

(defn log
  [this log]
  (put! (:output-ch this)
        log))
