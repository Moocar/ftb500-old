(ns me.moocar.ftb500.players
  (:require [clojure.pprint :refer [print-table]]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.request :as request])
  (:refer-clojure :exclude [find]))

(defn find-all-ids
  [db]
  {:pre [db]}
  (-> '[:find ?player
        :where [?player :player/name]]
      (d/q db)
      (->> (map first))))

(defn dbg-all
  ([db]
     {:pre [db]}
     (->> (find-all-ids db)
          (map #(select-keys (d/entity db %) [:db/id :player/name]))
          (print-table)))
  ([db player-ids]
     {:pre [db (sequential? player-ids)]}
     (->> player-ids
          (map #(select-keys (d/entity db %) [:db/id :player/name]))
          (print-table))))

(defn find
  [db player-ext-id]
  (when-let [player-id (-> '[:find ?player
                             :in $ ?player-id
                             :where [?player :player/id ?player-id]]
                           (d/q db player-ext-id)
                           ffirst)]
    (d/entity db player-id)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API

(defn add!
  [conn {:keys [player-name]}]
  (request/wrap-bad-args-response
   [(string? player-name) (not (string/blank? player-name))]
   (let [player-ext-id (d/squuid)]
     @(d/transact conn
                  [{:db/id (d/tempid :db.part/user)
                    :player/id player-ext-id
                    :player/name player-name}])
     {:status 200
      :body {:player-id player-ext-id}})))
