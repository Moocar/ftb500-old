(ns me.moocar.ftb500.http.websockets
  (:require [clojure.edn :as edn]
            [clojure.core.async :refer [go chan <!]]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty9 :as jetty]
            [me.moocar.ftb500.pubsub :as pubsub]))

(defn make-websocket-handlers
  [component]
  {:on-connect
   (fn [ws]
     (println "This is a connect" ws))

   :on-error
   (fn [ws e]
     (println "error" ws e))

   :on-close
   (fn [ws]
     (println "close" ws))

   :on-text
   (fn [ws text]
     (println text)
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
     (println "bytes" ws bytes offset len))})

(defrecord Websockets [pubsub]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (make-websocket-handlers this)))
  (stop [this]
    this))

(defn new-websockets
  [config]
  (component/using (map->Websockets {})
    [:pubsub]))
