(ns me.moocar.ftb500.client
  (:require [clojure.core.async :as async :refer [go-loop <! put! <!!]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.client.transport :as transport]))

(defn log
  [this msg]
  (log/log (:log this) msg))

(defn send!
  ([this route msg dont-send]
     (transport/send! (:client-transport this)
                      {:route route
                       :body msg}))
  ([this route msg]
     (let [response-ch (async/chan)]
       (transport/send! (:client-transport this)
                        {:route route
                         :body msg}
                        2000
                        (fn [response]
                          (put! response-ch response)))
       response-ch)))
