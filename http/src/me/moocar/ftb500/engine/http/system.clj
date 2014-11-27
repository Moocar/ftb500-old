(ns me.moocar.ftb500.engine.http.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.http :as http]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.ftb500.engine.transport.sente :as sente-transport]
            [ring.middleware.session.memory :as memory-session]))

(defn- http-system
  [config]
  (component/system-map
   :http-handler (http/new-http-handler)
   :http-server (http/new-http-server)
   :sente-transport (sente-transport/new-sente-transport)
   :user-store (sente-transport/new-sente-user-store)
   :session-store (memory-session/memory-store)))

(defn new-system
  [config]
  (merge
   (engine-system/new-system config)
   (http-system config)))
