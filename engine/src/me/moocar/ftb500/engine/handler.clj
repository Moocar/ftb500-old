(ns me.moocar.ftb500.engine.handler
  (:require [me.moocar.ftb500.engine.datomic :as db]
            [me.moocar.ftb500.engine.card :as card]))

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
   :user/id (fn [db args id]
                (assoc args :player (db/find db :user/id id)))
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
