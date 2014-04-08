(ns me.moocar.ftb500.players
  (:require [clojure.pprint :refer [print-table]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(def ref-player-names
  #{"Soap" "Eddy" "Bacon" "Winston" "Big Chris" "Barry the Baptist"
    "Little Chris" "Hatchet Harry" "Willie" "Dog" "Plank" "Nick the Greek"
    "Rory Breaker" "Traffic Warden" "Lenny" "JD"})

(defn add!
  [players player-name]
  {:pre [players (string? player-name)]}
  (let [conn (:conn (:db players))
        player-id (d/tempid :db.part/user)
        result @(d/transact conn
                            [[:db/add player-id :player/name player-name]])]
    (d/resolve-tempid (d/db conn) (:tempids result) player-id)))

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
