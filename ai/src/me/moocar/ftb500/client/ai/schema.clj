(ns me.moocar.ftb500.client.ai.schema
  (:require [me.moocar.ftb500.schema :as schema :refer [seat? player? game?]]))

(defn ai? [ai]
  (when (:seat ai)
    (schema/check-map ai {:seat seat?}))
  (schema/check-map ai {:game game?
                        :engine-transport map?
                        :game/num-players number?
                        :player player?
                        :route-pub-ch identity}))
