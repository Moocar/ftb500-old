(ns me.moocar.ftb500.client.listener
  (:require [clojure.core.async :as async :refer [go-loop <!]]
            [me.moocar.log :as log]))

(defn start
  [this]
  (let [{:keys [receive-ch logger client-id]} this]
    (if receive-ch
      this
      (let [receive-ch (async/chan)
            mult (async/mult receive-ch)
            log-ch (async/chan)]
        (async/tap mult log-ch)
        (go-loop []
          (when-let [msg (<! log-ch)]
            (log/log logger (format "  CLIENT:%8.8s %s"
                                    (str client-id)
                                    msg))
            (recur)))
        (assoc this
          :mult mult
          :receive-ch receive-ch)))))

(defn stop
  [this]
  (let [{:keys [receive-ch mult]} this]
    (if receive-ch
      (do (async/close! receive-ch)
          (assoc this
            :mult nil
            :receive-ch nil))
      this)))

(defn new-client-listener [logger client-id]
  {:logger logger
   :client-id client-id})
