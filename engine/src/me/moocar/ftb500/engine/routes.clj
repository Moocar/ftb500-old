(ns me.moocar.ftb500.engine.routes
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.datomic :as db]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.user :as user-lookup]))

(defprotocol Route
  (serve [this db request]))

(defmacro bad-args?
  [forms]
  `(when-not (empty? (keep (fn [rule#]
                             (when-not rule#
                               true))
                           ~forms))
     '~forms))

(defmacro with-bad-args
  [args & body]
  `(let [errors# (bad-args? ~args)]
     (if (not (empty? errors#))
       [:bad-args {:msg errors#}]
       (do ~@body))))

(defn logged-callback
  [this message]
  (fn [& args]
    (when (keyword? (first args))
      (log/log (:log this) {:ERROR (first args)
                            :request message}))
    (apply (:callback message) args)))

(defn route
  [this message]
  (let [message (assoc message :callback (logged-callback this message))
        {:keys [body logged-in-user-id client-id route callback]} message
        route-ns-keyword (keyword "routes" (name route))]
    (log/log (:log this) (format "%8.8s:%8.8s %-10.10s %s"
                                 (if logged-in-user-id
                                   (str logged-in-user-id)
                                   "ANON")
                                  (str client-id)
                                  route
                                  body))
    (if-let [server (get this route-ns-keyword)]
      (let [{:keys [datomic]} this
            conn (:conn datomic)
            db (d/db conn)]
        (serve server db message))
      (when callback
        (callback :ftb500/no-route)))))

(defn new-router []
  (component/using {}
    [:log
     :datomic
     :routes/add-game
     :routes/bid
     :routes/bid-table
     :routes/game-info
     :routes/join-game
     :routes/login
     :routes/logout
     :routes/signup
     :routes/exchange-kitty
     :routes/play-card]))
