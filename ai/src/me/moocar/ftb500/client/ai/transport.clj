(ns me.moocar.ftb500.client.ai.transport
  (:require [me.moocar.async :as moo-async :refer [<? go-try]]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.log :as log]))

(defn log [ai msg]
  (log/log (:log ai) msg))

(defn send!
  [ai route msg]
  {:pre [(:transport ai)]}
  (let [send-ch (:send-ch (:conn (:transport ai)))]
    (go-try
     (let [response (<? (moo-async/request send-ch
                                           {:route route
                                            :body msg}))
           {:keys [body]} response]
       (if (or (keyword? body) (not= :success (first body)))
         (do (log ai {:ERROR body})
             (throw (ex-info "Error in Send"
                             {:error body
                              :route route
                              :request msg})))
         body)))))

(defn game-send!
  [ai route msg]
  (let [new-msg (assoc msg
                  :seat/id (:seat/id (:seat ai)))]
    (log ai {:route route :msg new-msg})
    (send! ai route new-msg)))
