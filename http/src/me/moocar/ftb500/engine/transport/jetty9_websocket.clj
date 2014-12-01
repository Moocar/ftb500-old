(ns me.moocar.ftb500.engine.transport.jetty9-websocket
  (:require [clojure.core.async :as async :refer [go-loop <! >!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.transport :as engine-transport]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]
            [ring.middleware.session.store :as session-store]))

(defn start [this]
  {:on-connect 
   (fn [ws]
     (println "Got a connection!" ws))

   :on-error 
   (fn [ws e])

   :on-close 
   (fn [ws status-code reason]
     (println "closed" ws status-code reason))

   :on-text 
   (fn [ws text-message]
     (println "got text" ws text-message))

   :on-bytes 
   (fn [ws bytes offset len]
     (println "got bytes" ws bytes offset len))})

(defrecord Jetty9WebSocket []
  component/Lifecycle
  (start [this]
    (start this))
  (stop [this]
    this))

(defn new-jetty9-websocket [config]
  (map->Jetty9WebSocket {}))


