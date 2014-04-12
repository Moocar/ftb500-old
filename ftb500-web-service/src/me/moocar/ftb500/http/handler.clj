(ns me.moocar.ftb500.http.handler
  (:require [com.stuartsierra.component :as component]))

(defrecord HandlerComponent []
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (fn [request] {:status 200 :body "Great success"})))
  (stop [this]
    this))

(defn new-handler
  [config]
  (map->HandlerComponent {}))
