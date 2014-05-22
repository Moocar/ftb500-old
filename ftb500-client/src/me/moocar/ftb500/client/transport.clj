(ns me.moocar.ftb500.client.transport
  (:require [clojure.core.async :refer [chan go alts! timeout <! >! go-loop]]
            [com.stuartsierra.component :as component]))

(defprotocol Transporter
  (register [this client-id request-ch response-ch]))

(defn register-response-ch
  [this seq-id ch]
  (swap! (:open-requests this) assoc seq-id ch))

(defn de-register-response-ch
  [this seq-id]
  (swap! (:open-requests this) dissoc seq-id))

(defn request
  [this payload]
  (let [{:keys [request-ch seq-id-atom]} this
        seq-id (swap! seq-id-atom inc)]
    (go
      (try
        (>! request-ch {:seq-id seq-id
                        :payload payload})
        (let [response-ch (chan)]
          (register-response-ch this seq-id response-ch)
          (let [[response _] (alts! [response-ch (timeout 30000)])]
            (de-register-response-ch this seq-id)
            response))
        (catch Throwable t
          (.printStackTrace t)
          (throw t))))))

(defn start-listen-loop
  [this]
  (let [{:keys [handler response-ch open-requests]} this]
    (go-loop []
      (try
        (when-let [packet (<! response-ch)]
          (let [{:keys [seq-id payload]} packet]
            (if-not seq-id
              (handler payload)
              (when-let [open-request-ch (get @open-requests seq-id)]
                (>! open-request-ch payload))))
          (recur))
        (catch Throwable t
          (.printStackTrace t)
          (throw t))))))

(defrecord ClientTransport
    [transport handler request-ch response-ch seq-id-atom open-requests]
  component/Lifecycle
  (start [this]
    (start-listen-loop this)
    (register transport "client-id" request-ch response-ch)
    this)
  (stop [this]
    this))

(defn new-client-transport
  []
  (component/using (map->ClientTransport {:request-ch (chan)
                                          :response-ch (chan)
                                          :seq-id-atom (atom 0)
                                          :open-requests (atom {})})
    {:transport :requester
     :handler :transport-handler}))
