(ns me.moocar.ftb500.engine.user
  (:require [datomic.api :as d]))

(defn lookup
  [this db user-id]
  (-> '[:find ?user
        :in $ ?user-id
        :where [?user :user/user-id ?user-id]]
      (d/q db user-id)
      ffirst
      (->> (d/entity db))))
