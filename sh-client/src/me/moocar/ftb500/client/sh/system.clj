(ns me.moocar.ftb500.client.sh.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport.jetty-ws :as jetty-ws-client-transport]))

(defn new-system [config]
  (component/system-map
   :client-transport (jetty-ws-client-transport/new-java-ws-client-transport config)
   :handler-xf (jetty-ws-client-transport/make-handler-xf)))
