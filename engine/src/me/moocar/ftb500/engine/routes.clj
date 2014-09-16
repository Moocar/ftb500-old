(ns me.moocar.ftb500.engine.routes
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.datomic :as db]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.user :as user-lookup]))

(defprotocol Route
  (serve [this db user msg] [this db user msg callback]))

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

(def replace-fns
  {:seat/id (fn [db args id]
              (let [seat (db/find db :seat/id id)]
                (assoc args
                  :seat seat
                  :game (first (:game/_seats seat)))))
   :game/id (fn [db args id]
              (assoc args :game (db/find db :game/id id)))
   :player/id (fn [db args id]
                (assoc args :player (db/find db :player/id id)))
   :bid-k (fn [db args id]
            (assoc args :bid (db/find db :bid/name id)))
   :card (fn [db args card]
           (assoc args :card (card/find db card)))})

(defn load-arg-entities
  [db args]
  {:pre [db (map? args)]}
  (reduce-kv
   (fn [m k v]
     (if-let [f (get replace-fns k)]
       (f db m v)
       (assoc m k v)))
   {}
   args))

(defn route
  [this message]
  (let [{:keys [user-id msg route callback]} message
        route-ns-keyword (keyword "routes" (name route))]
    (if-let [server (get this route-ns-keyword)]
      (let [{:keys [datomic user-lookup]} this
            conn (:datomic)
            db (d/db conn)
            msg-with-entities (load-arg-entities db msg)
            user (user-lookup/lookup user-lookup db user-id)]
       (if callback
         (serve server db user msg-with-entities callback)
         (serve server db user msg-with-entities)))
      (when callback
        (callback :ftb500/no-route)))))

(defn new-router []
  (component/using {}
    [:routes/add-game]))
