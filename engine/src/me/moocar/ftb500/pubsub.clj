(ns me.moocar.ftb500.pubsub
  (:require [clojure.core.async :refer [put!]]
            [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.log :as log]))

(defn register-client
  [this client-map]
  (log/log (:log this) {:msg "register-client"
                        :client-map client-map})
  (let [output-ch (:output-ch client-map)]
    (put! output-ch {:action :registered})
    (swap! (:client-db this)
          conj client-map)))

(defn start-db-listener
  [component]
  (let [datomic (:datomic component)
        tx-report-queue (:tx-report-queue datomic)
        conn (:conn datomic)
        attr-id (:id (d/attribute (d/db conn) :game.bid/bid))]
    (future
      (log/log (:log component) {:msg "Starting pubsub loop"})
      (loop [tx (.take tx-report-queue)]
        (try
          (log/log (:log component)
                   {:msg "DB event"
                    :event (-> '[:find ?eid ?attrid
                                 :where [?eid ?attrid]]
                               (d/q (:tx-data tx) attr-id))})
          (catch Throwable e
            (log/log (:log component)
                     {:msg "error in pubsub loop"
                      :ex e})))
        (recur (.take tx-report-queue))))))

(defrecord Pubsub [datomic client-db log]
  component/Lifecycle
  (start [this]
    (start-db-listener this)
    this)
  (stop [this]
    (d/remove-tx-report-queue (:conn datomic))
    this))

(defn new-pubsub
  [config]
  (component/using (map->Pubsub {:client-db (atom [])})
    [:datomic :log]))
