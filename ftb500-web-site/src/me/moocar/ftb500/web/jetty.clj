(ns me.moocar.ftb500.web.jetty
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]))

(defrecord JettyHttp [port jetty handler]
  component/Lifecycle
  (start [this]
    (assoc this
      :jetty (jetty/run-jetty (:handler handler)
                              {:join? false
                               :port port})))
  (stop [this]
    (when jetty
      (.stop jetty)
      this)))

(defn new-jetty-http
  [config]
  (let [port (:port config)]
    (assert (number? port))
    (component/using (map->JettyHttp {:port port})
      [:handler])))
