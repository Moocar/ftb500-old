(ns me.moocar.ftb500.engine.transport.websocket.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.transport.websocket]))

(defn new-system [config]
  (component/system-map
   :transport (me.moocar.ftb500.engine.transport.websocket/new-websocket-server config)
   :engine-handler-xf (keep (fn [request] 
                              (println "got request")))))

