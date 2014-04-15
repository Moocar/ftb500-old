(ns me.moocar.ftb500.pubsub
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]))

(defrecord Pubsub [datomic]
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
  (component/using (map->Pubsub {})
    [:datomic]))
