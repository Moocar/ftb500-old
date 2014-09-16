(ns me.moocar.ftb500.client
  (:require [clojure.core.async :as async :refer [go-loop <! put! <!!]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.client.transport :as transport]))

(defn log
  [this msg]
  (log/log (:log this) msg))

(defn send!
  ([this route msg]
     (transport/send! (:client-transport this)
                      {:route route
                       :msg msg}))
  ([this route msg expect-response?]
     (let [response-ch (async/chan)]
       (transport/send! (:client-transport this)
                        {:route route
                         :msg msg}
                        1000
                        (fn [response]
                          (put! response-ch response)))
       (<!! response-ch))))
