(ns me.moocar.ftb500.client.transport.sente
  (:require [clojure.core.async :as async :refer [go-loop put! <!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport :as client-transport]
            [taoensso.sente :as sente]))

(defrecord ClientSenteTransport [client-listener send-fn ch-recv]

  component/Lifecycle
  (start [this]
    (if send-fn
      this
      (let [sente (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                              {:type :auto})
            {:keys [ch-recv send-fn]} sente]
        (go-loop []
          (let [msg (<! ch-recv)
                {:keys [ring-req event ?reply-fn]} msg
                [_ msg] event]
            (put! (:receive-ch client-listener) msg)
            (recur)))
        (merge this sente))))
  (stop [this]
    (if send-fn
      (do (async/close! ch-recv)
          (assoc this
            :send-fn nil
            :ch-recv nil))
      this))

  client-transport/ClientTransport
  (-send! [this msg]
    (let [{:keys [route]} msg]
      (assert (keyword? route))
      (send-fn [route (dissoc msg :route)])))
  (-send! [this msg timeout-ms callback]
    (let [{:keys [route]} msg]
      (assert (keyword? route))
      (send-fn [route (dissoc msg :route)] timeout-ms callback))))

(defn new-client-sente-transport []
  (component/using (map->ClientSenteTransport {})
    [:client-listener]))
