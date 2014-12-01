(ns me.moocar.ftb500.engine.http.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.http :as http]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.engine.transport.jetty9-websocket :as jetty9-websocket]
            [ring.middleware.session.memory :as memory-session]))

(defn- http-system
  [config]
  (component/system-map
   :jetty9-websocket (jetty9-websocket/new-jetty9-websocket config)
   :http-server (http/new-http-server config)
   :http-handler (http/new-http-handler config)
   :session-store (memory-session/memory-store)))

(defn new-system
  [config]
  (merge
   (engine-system/new-system config)
   (http-system config)))
