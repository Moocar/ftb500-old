(ns me.moocar.ftb500.client
  (:require [me.moocar.async :as moo-async :refer [<? go-try]]
            [me.moocar.log :as log]))

(defn log [client msg]
  (log/log (:log client) msg))

(defn send!
  [client route msg]
  {:pre [(:transport client)]}
  (let [send-ch (:send-ch (:conn (:transport client)))]
    (go-try
     (let [response (<? (moo-async/request send-ch
                                           {:route route
                                            :body msg}))
           {:keys [body]} response]
       (if (or (keyword? body) (not= :success (first body)))
         (do (log client {:ERROR body})
             (throw (ex-info "Error in Send"
                             {:error body
                              :route route
                              :request msg})))
         body)))))

(defn game-send!
  [client route msg]
  (let [new-msg (assoc msg
                  :seat/id (:seat/id (:seat client)))]
    (log client {:route route :msg new-msg})
    (send! client route new-msg)))
