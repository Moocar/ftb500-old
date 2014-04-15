(ns me.moocar.ftb500.http.jetty
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty9 :as jetty]))

(defrecord JettyHttp [port jetty handler websockets]
  component/Lifecycle
  (start [this]
    (assoc this
      :jetty (jetty/run-jetty (:handler handler)
                              {:join? false
                               :port port
                               :websockets {"/ws" (:handler websockets)}})))
  (stop [this]
    (when jetty
      (.stop jetty)
      this)))

(defn new-jetty-http
  [config]
  (let [port (:port config)]
    (assert (number? port))
    (component/using (map->JettyHttp {:port port})
      [:handler :websockets])))
