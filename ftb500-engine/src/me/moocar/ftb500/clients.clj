(ns me.moocar.ftb500.clients
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [put! close! alts! timeout chan <! >! go go-loop]]))

(def ^:private default-timeout 30000)

(defn- find-client
  [this client-id]
  (get (:clients @(:db this)) client-id))

(defn- respond
  [client seq-id payload]
  (put! (:out-ch client)
        {:seq-id seq-id
         :payload payload}))

(defn publish
  [this client-id msg]
  (let [client (find-client this client-id)]
    (assert client (str "Client '" client-id "' not registered"))
    (respond client nil msg)))

(defn- handle-packet
  [this client packet]
  (let [response-ch (chan)]
    ((:handler this) client (:payload packet) response-ch)
    (go
      (try
        (let [[response port] (alts! [response-ch (timeout default-timeout)])]
          (if response
            (respond client (:seq-id packet) response)
            (println "timed out" (:seq-id packet))))
        (catch Throwable t
          (.printStackTrace t)
          (throw t)))
      (close! response-ch))
    ((:handler-fn (:handler this)) client (:payload packet) response-ch)))

(defn- start-listen-loop
  [this client]
  (go-loop []
    (when-let [packet (<! (:in-ch client))]
      (try
        (handle-packet this client packet)
        (catch Throwable t
          (.printStackTrace t)
          (throw t)))
      (recur))))

(defn register
  [this client-id in-ch out-ch]
  (let [client {:id client-id
                :in-ch in-ch
                :out-ch out-ch}]
    (swap! (:db this)
           update-in [:clients]
           assoc client-id client)
    (start-listen-loop this client)))

(defn de-register
  [this client-id]
  (swap! (:db this) update-in [:clients] dissoc client-id))

(defrecord Clients [handler db])

(defn new-clients
  []
  (component/using (map->Clients {:db (atom {:clients {}})})
    {:handler :clients-handler}))

(defn echo-handler
  [client payload response-ch]
  (put! response-ch payload))
