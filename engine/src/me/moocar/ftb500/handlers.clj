(ns me.moocar.ftb500.handlers
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.players :as players]))

(defn not-found-handler
  [_ request]
  {:status 404
   :body {:msg "No Service Found"}})

(defn make-handler-lookup
  []
  {:create-player players/add!
   :create-game game/add!})

(defn handle-request
  [component request]
  (let [conn (:conn (:datomic component))
        handler-lookup (:handler-lookup component)
        {:keys [action args]} request
        handler-fn (get handler-lookup action not-found-handler)]
    (handler-fn conn args)))

(defrecord HandlerComponent [datomic]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler-lookup (make-handler-lookup)))
  (stop [this]
    this))

(defn new-handler-component
  []
  (component/using (map->HandlerComponent {})
    [:datomic]))
