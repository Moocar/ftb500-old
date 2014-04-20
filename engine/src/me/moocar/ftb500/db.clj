(ns me.moocar.ftb500.db
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.log :as log])
  (:import [java.io PushbackReader])
  (:refer-clojure :exclude [find]))

(def ^:private mem-uri
  "datomic:mem://ftb500")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; API

(defn uuid? [s]
  (instance? java.util.UUID s))

(defn find
  [db entity-id-key ext-id]
  {:pre [db (keyword? entity-id-key) (uuid? ext-id)]}
  (when-let [entity-id (-> '[:find ?entity
                             :in $ ?entity-id-key ?entity-id
                             :where [?entity ?entity-id-key ?entity-id]]
                           (d/q db entity-id-key ext-id)
                           ffirst)]
    (d/entity db entity-id)))

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
                        :where [?e :card.rank/name :card.rank.name/four]]
                      db)))))

(defn ensure-ref-data
  [conn]
  (when-not (ref-data-exists? conn)
    (transact-resource conn "ref_cards.edn")))

(defn del-db
  []
  (d/delete-database mem-uri))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component

(defrecord DatomicDatabase [uri log]
  component/Lifecycle
  (start [this]
    ;; Ensure database created
    (when (d/create-database uri)
      (log/log log {:msg "Created database"
                    :uri uri}))
    (let [conn (d/connect uri)]
      (ensure-schema conn)
      (ensure-ref-data conn)
      (assoc this
        :conn conn
        :tx-report-queue (d/tx-report-queue conn))))
  (stop [this]
    (when-let [conn (:conn this)]
      (log/log log {:msg "removing tx report q"})
      (d/remove-tx-report-queue conn)
      (d/release conn))
    (assoc this
      :conn nil
      :tx-report-queue nil)))

(defn new-datomic-database
  []
  (component/using (map->DatomicDatabase {:uri mem-uri})
    [:log]))
