(ns me.moocar.log
  (:require [clojure.pprint :refer [pprint]]))

(defn format-log [log]
  (if (map? log)
    (let [log (into {} log)
          non-ex (dissoc log :ex)]
      (with-out-str (pprint non-ex))
      (when (:ex log)
        (with-out-str (.printStackTrace (:ex log)))))
    (if (string? log)
      log
      (if (instance? Throwable log)
        (with-out-str (.printStackTrace log))
        (with-out-str (pprint log))))))
