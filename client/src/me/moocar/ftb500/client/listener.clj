(ns me.moocar.ftb500.client.listener
  (:require [clojure.core.async :as async :refer [go-loop <!]]
            [me.moocar.log :as log]))

(defn start
  [this]
  (let [{:keys [receive-ch logger]} this]
    (if receive-ch
      this
      (let [receive-ch (async/chan)]
        (go-loop []
          (when-let [msg (<! receive-ch)]
            (log/log logger (str "Client received: " msg))
            (recur)))
        (assoc this
          :receive-ch receive-ch)))))

(defn stop
  [this]
  (let [{:keys [receive-ch]} this]
    (if receive-ch
      (do (async/close! receive-ch)
          (assoc this
            :receive-ch nil))
      receive-ch)))

(defn new-client-listener [logger]
  {:logger logger})
