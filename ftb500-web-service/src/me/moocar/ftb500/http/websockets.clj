(ns me.moocar.ftb500.http.websockets
  (:require [clojure.edn :as edn]
            [clojure.core.async :refer [go chan <!]]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty9 :as jetty]
            [me.moocar.log :as log]
            [me.moocar.ftb500.pubsub :as pubsub]))

(defn make-websocket-handlers
  [component]
  {:on-connect
   (fn [ws]
     (log/log (:log component) {:msg "WS connect"}))

   :on-error
   (fn [ws e]
     (log/log (:log component) {:msg "WS Error"
                                :ex e}))

   :on-close
   (fn [ws]
     (log/log (:log component) {:msg "WS close"}))

   :on-text
   (fn [ws text]
     (log/log (:log component) {:msg "WS text"
                                :text text})
     (let [client-map (edn/read-string text)
           output-ch (chan)]
       (go
         (loop []
           (when-let [msg (<! output-ch)]
             (jetty/send-text ws (pr-str msg))
             (recur))))
       (pubsub/register-client (:pubsub component)
                               (assoc client-map
                                 :output-ch output-ch))))

   :on-bytes
   (fn [ws bytes offset len]
     (log/log (:log component) {:msg "WS bytes"
                                :bytes bytes}))})

(defrecord Websockets [pubsub log]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (make-websocket-handlers this)))
  (stop [this]
    this))

(defn new-websockets
  [config]
  (component/using (map->Websockets {})
    [:pubsub :log]))
