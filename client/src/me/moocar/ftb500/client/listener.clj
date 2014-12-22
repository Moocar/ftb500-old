(ns me.moocar.ftb500.client.listener
  (:require [clojure.core.async :as async :refer [go-loop <!]]
            [me.moocar.log :as log]))

(defn client-log
  [this msg]
  (log/log (:logger this)
           (format "  CLIENT:%8.8s %s"
                   (str (:client-id this))
                   msg))
  nil)

(defn start
  [this]
  (let [{:keys [mult receive-ch]} this]
    (if mult
      this
      (let [mult (async/mult receive-ch)
            log-ch (async/chan 1 (keep #(client-log this %)))]
        (async/tap mult log-ch)
        (assoc this
          :mult mult
          :receive-ch receive-ch)))))

(defn stop
  [this]
  (let [{:keys [mult]} this]
    (if mult
      (assoc this
        :mult nil)
      this)))

(defn new-client-listener [logger receive-ch client-id]
  {:logger logger
   :client-id client-id
   :receive-ch receive-ch})
