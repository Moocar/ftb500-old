(ns me.moocar.ftb500.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.handlers :as handler]
            [me.moocar.log :as log]
            [me.moocar.ftb500.pubsub2 :as pubsub]))

(defn new-system
  []
  (let [config {:datomic {:uri "datomic:free://localhost:4334/ftb500"}}]
   (component/system-map
    :datomic (db/new-datomic-database config)
    :handler (handler/new-handler-component)
    :log (log/new-logger config)
    :pubsub (pubsub/new-pubsub config))))
