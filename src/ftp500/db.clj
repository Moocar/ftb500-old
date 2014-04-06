(ns ftb500.db
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [datomic.api :as d])
  (:import [java.io PushbackReader]))

(defn transact-resource
  [conn resource-name]
  (with-open [reader (PushbackReader. (jio/reader (jio/resource resource-name)))]
    (d/transact conn (edn/read {:readers {'db/id datomic.db/id-literal}}
                               reader))))

(defn ensure-schema
  [conn]
  (transact-resource conn "db_schema.edn"))

(defn ref-data-exists?
  [conn]
  (let [db (d/db conn)]
    (some? (d/q '[:find ?e
                  :where [?e :bid/name :bid.name/pass]]
                db))))

(defn ensure-ref-data
  [conn]
  (when-not (ref-data-exists? conn)
    (transact-resource conn "ref_data.edn")))

(defrecord DatomicDatabase [uri]
  component/Lifecycle
  (start [this]
    ;; Ensure database created
    (d/create-database uri)
    (let [conn (d/connect uri)]
      (ensure-schema conn)
      (ensure-ref-data conn)
      (assoc this :conn conn)))
  (stop [this]
    this))

(defn new-datomic-database
  []
  (->DatomicDatabase "datomic:mem://ftp500"))
