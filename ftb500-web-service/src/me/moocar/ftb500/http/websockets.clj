(ns me.moocar.ftb500.http.websockets
  (:require [clojure.edn :as edn]
            [com.stuartsierra.component :as component]
            [ring.adapter.jetty9 :as jetty]))

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
     (let [client-map (edn/read-string text)]
       (swap! (:connections component)
              conj (assoc client-map
                     :ws ws)))
     (jetty/send-text ws "Got it!"))

   :on-bytes
   (fn [ws bytes offset len]
     (println "bytes" ws bytes offset len))})

(defrecord Websockets []
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (make-websocket-handlers this)))
  (stop [this]
    this))

(defn new-websockets
  [config]
  (map->Websockets {}))
