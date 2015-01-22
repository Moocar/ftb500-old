(ns me.moocar.ftb500.client.schema
  (:require [me.moocar.ftb500.schema :as schema :refer [seat? player? game?]]))

(defn client? [client]
  (when (:seat client)
    (schema/check-map client {:seat seat?}))
  (schema/check-map client {:game game?
                            :engine-transport map?
                            :game/num-players number?
                            :player player?
                            :route-pub-ch identity}))
