(ns me.moocar.ftb500.web.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.log :as log]
            [me.moocar.ftb500.web.handler :as handler]
            [me.moocar.ftb500.web.jetty :as jetty]))

(defn new-system
  []
  (let [config {:port 8080}]
    (component/system-map
     :handler (handler/new-handler config)
     :jetty-http (jetty/new-jetty-http config)
     :log (log/new-logger config))))
