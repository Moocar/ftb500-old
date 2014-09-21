(ns me.moocar.log
  (:require [clojure.core.async :refer [go-loop chan <! put! close!]]
            [clojure.pprint :refer [pprint]]
            [com.stuartsierra.component :as component]))

(defrecord Logger [output-ch started?]
  component/Lifecycle
  (start [this]
    (if output-ch
      this
      (let [output-ch (chan 1024)]
        (go-loop []
          (when-let [log (<! output-ch)]
            (try
              (if (map? log)
                (let [log (into {} log)
                      non-ex (dissoc log :ex)]
                  (pprint non-ex)
                  (when (:ex log)
                    (.printStackTrace (:ex log))))
                (if (string? log)
                  (println log)
                  (if (instance? Throwable log)
                    (.printStackTrace log)
                    (pprint log))))
              (catch Throwable t
                (.printStackTrace t)))
            (recur)))
        (assoc this
          :output-ch output-ch))))
  (stop [this]
    (if output-ch
      (do (close! output-ch)
          (assoc this :output-ch nil))
      this)))

(defn new-logger
  [config]
  (map->Logger {}))

(defn log
  [this log]
  (put! (:output-ch this)
        log))
