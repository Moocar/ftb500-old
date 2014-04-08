(ns me.moocar.ftb500.db
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [datomic.api :as d])
  (:import [java.io PushbackReader]))

(def ^:private mem-uri
  "datomic:mem://ftb500")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Setup/Teardown

(defn transact-resource
  [conn resource-name]
  (with-open [reader (PushbackReader. (jio/reader (jio/resource resource-name)))]
    @(d/transact conn (edn/read {:readers {'db/id datomic.db/id-literal}}
                                reader))))

(defn ensure-schema
  [conn]
  (transact-resource conn "db_schema.edn"))

(defn ref-data-exists?
  [conn]
  (let [db (d/db conn)]
    (not (empty? (d/q '[:find ?e
                        :where [?e :card/rank :card.rank/four]]
                      db)))))

(defn ensure-ref-data
  [conn]
  (when-not (ref-data-exists? conn)
    (println "Adding reference data")
    (transact-resource conn "ref_data.edn")
    (transact-resource conn "ref_cards.edn")))

(defn del-db
  []
  (d/delete-database mem-uri))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component

(defrecord DatomicDatabase [uri]
  component/Lifecycle
  (start [this]
    ;; Ensure database created
    (when (d/create-database uri)
      (println "Created database" uri))
    (let [conn (d/connect uri)]
      (ensure-schema conn)
      (ensure-ref-data conn)
      (assoc this :conn conn)))
  (stop [this]
    this))

(defn new-datomic-database
  []
  (->DatomicDatabase mem-uri))
