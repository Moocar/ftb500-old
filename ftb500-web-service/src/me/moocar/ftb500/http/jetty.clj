(ns me.moocar.ftb500.http.jetty
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty :as jetty]))

(defrecord JettyHttp [port handler jetty]
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
