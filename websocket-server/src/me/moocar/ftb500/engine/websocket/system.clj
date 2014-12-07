(ns me.moocar.ftb500.engine.websocket.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.engine.websocket :as websocket-server]))

(defn- websocket-system
  [config]
  (component/system-map
   :handler-xf (websocket-server/make-handler-xf)
   :websocket-server (websocket-server/new-websocket-server config)))

(defn new-system
  [config]
  (merge
   (engine-system/new-system config)
   (websocket-system config)))

