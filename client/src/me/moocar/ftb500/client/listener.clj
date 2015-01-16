(ns me.moocar.ftb500.client.listener
  (:require [clojure.core.async :as async :refer [go-loop <!]]))

(defn format-log [this msg]
  (format "  CLIENT:%8.8s %s"
          (str (:client-id this))
          msg))

(defn start
  [this]
  (let [{:keys [mult receive-ch log-ch]} this]
    (if mult
      this
      (let [mult (async/mult receive-ch)
            format-log-ch (async/chan 1 (map #(format-log this %)))]
        (async/pipe format-log-ch log-ch)
        (async/tap mult format-log-ch)
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

(defn new-client-listener [log-ch receive-ch client-id]
  {:log-ch log-ch
   :client-id client-id
   :receive-ch receive-ch})
