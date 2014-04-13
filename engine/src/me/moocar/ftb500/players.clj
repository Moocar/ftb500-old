(ns me.moocar.ftb500.players
  (:require [clojure.pprint :refer [print-table]]
            [clojure.string :as string]
            [com.stuartsierra.component :as component]
            [datomic.api :as d])
  (:refer-clojure :exclude [find]))

(def ref-player-names
  #{"Soap" "Eddy" "Bacon" "Winston" "Big Chris" "Barry the Baptist"
    "Little Chris" "Hatchet Harry" "Willie" "Dog" "Plank" "Nick the Greek"
    "Rory Breaker" "Traffic Warden" "Lenny" "JD"})

(defn add-players!
  [players player-names]
  {:pre [players (sequential? player-names)]}
  (let [conn (:conn (:db players))]
    @(d/transact conn
                 (map #(vector :db/add (d/tempid :db.part/user)
                               :player/name %)
                      player-names))))

(defn find-all-ids
  [db]
  {:pre [db]}
  (-> '[:find ?player
        :where [?player :player/name]]
      (d/q db)
      (->> (map first))))

(defn dbg-all
  ([players]
     {:pre [players]}
     (let [db (d/db (:conn (:db players)))]
       (->> (find-all-ids db)
            (map #(select-keys (d/entity db %) [:db/id :player/name]))
            (print-table))))
  ([players player-ids]
     {:pre [players (sequential? player-ids)]}
     (let [db (d/db (:conn (:db players)))]
       (->> player-ids
            (map #(select-keys (d/entity db %) [:db/id :player/name]))
            (print-table)))))

(defn find
  [db player-ext-id]
  (when-let [player-id (-> '[:find ?player
                             :in $ ?player-id
                             :where [?player :player/id ?player-id]]
                           (d/q db player-ext-id)
                           ffirst)]
    (d/entity db player-id)))

(defrecord Players [mode db]
  component/Lifecycle
  (start [this]
    (when (= :dev mode)
      (when (empty? (find-all-ids (d/db (:conn db))))
        (println "adding mock players")
        (add-players! this (take 4 (shuffle ref-player-names)))))
    this)
  (stop [this]
    this))

(defn new-players-component
  []
  (component/using
    (map->Players {:mode :dev})
    [:db]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API

(defmacro bad-arguments?
  [forms]
  `(when-not (empty? (keep (fn [rule#]
                             (when-not rule#
                               true))
                           ~forms))
     '~forms))

(defn add!
  [conn {:keys [player-name]}]
  (let [errors (bad-arguments? [(string? player-name)
                                (not (string/blank? player-name))])]
    (if (not (empty? errors))
      {:status 400
       :body {:msg errors}}
      (let [player-ext-id (d/squuid)]
        @(d/transact conn
                     [{:db/id (d/tempid :db.part/user)
                       :player/id player-ext-id
                       :player/name player-name}])
        {:status 200
         :body {:player-id player-ext-id}}))))
