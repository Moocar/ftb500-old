(ns me.moocar.ftb500.pubsub
  (:require [clojure.core.async :refer [put!]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(defn register-client
  [this client-map]
  (println "registering client" client-map)
  (let [output-ch (:output-ch client-map)]
    (put! output-ch {:action :registered})
    (swap! (:client-db this)
          conj client-map)))

(defrecord Pubsub [datomic client-db]
  component/Lifecycle
  (start [this]
    (let [tx-report-queue (:tx-report-queue datomic)
          conn (:conn datomic)
          attr-id (:id (d/attribute (d/db conn) :game.bid/bid))]
      (future
        (println "starting pubsub loop")
        (loop [tx (.take tx-report-queue)]
          (try
            (println
             (-> '[:find ?eid ?attrid
                   :where [?eid ?attrid]]
                 (d/q (:tx-data tx) attr-id)))
            (catch Throwable e
              (.printStackTrace e)))
          (recur (.take tx-report-queue)))))))

(defn new-pubsub
  [config]
  (component/using (map->Pubsub {:client-db (atom [])})
    [:datomic]))
