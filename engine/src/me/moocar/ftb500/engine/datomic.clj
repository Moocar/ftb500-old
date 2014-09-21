(ns me.moocar.ftb500.engine.datomic
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.log :as log])
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

(defn- action-tx
  [game-id action]
  (let [tx-id (d/tempid :db.part/tx)]
    [[:db/add tx-id :tx/game-id game-id]
     [:db/add tx-id :action action]]))

(defn transact-action
  [this tx game-id action]
  (let [conn (:conn this)]
    (d/transact conn (concat tx (action-tx game-id action)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Setup/Teardown

(defn transact-resource
  [conn resource-name]
  (let [resource (jio/resource resource-name)]
    (when-not resource
      (throw (Exception. (str "Could not find Resource: " resource-name))))
    (with-open [reader (PushbackReader. (jio/reader (jio/resource resource-name)))]
      @(d/transact conn (edn/read {:readers {'db/id datomic.db/id-literal
                                             'db/fn datomic.function/construct}}
                                  reader)))))

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
      (d/remove-tx-report-queue conn)
      (d/release conn))
    (assoc this
      :conn nil
      :tx-report-queue nil)))

(defn new-datomic-database
  [config]
  (component/using (map->DatomicDatabase (merge {:uri mem-uri}
                                                (:datomic config)))
    [:log]))
