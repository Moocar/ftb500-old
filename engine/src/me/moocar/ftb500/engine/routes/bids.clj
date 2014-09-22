(ns me.moocar.ftb500.engine.routes.bids
  (:require [datomic.api :as d]
            [me.moocar.log :as log]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport :as transport]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]))

(defn ext-form
  [bid]
  (-> bid
      (select-keys [:bid/name
                    :bid/rank
                    :bid/suit
                    :bid/contract-style
                    :bid/score])
      (update-in [:bid/rank] :card.rank/name)
      (update-in [:bid/suit] :card.suit/name)))

(defrecord BidTable [datomic log]
  routes/Route
  (serve [this db request]
    (let [{:keys [callback]} request]
      (callback
       (->> (datomic/find db :bid/name)
            (sort-by :bid/score)
            (map ext-form))))))
