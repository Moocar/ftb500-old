(ns me.moocar.ftb500.client.ai.transport
  (:require [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.client :as client]
            [me.moocar.log :as log]))

(defn log [ai msg]
  (log/log (:log ai) msg))

(defn send!
  ([ai route msg dont-send]
     (client/send! ai route msg dont-send))
  ([ai route msg]
     (go-try
       (let [response (<? (client/send! ai route msg))]
         (if (keyword? response)
           (let [error (ex-info "Error in Send"
                                {:error response
                                 :route route
                                 :request msg})]
             (log ai error)
             (throw ex-info))
           response)))))

