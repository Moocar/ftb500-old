(ns me.moocar.ftb500.client.transport.sente
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport :as client-transport]
            [taoensso.sente :as sente]))

(defrecord ClientSenteTransport [send-fn client-receive-ch]
  component/Lifecycle
  (start [this]
    (let [sente (sente/make-channel-socket! "/chsk" ; Note the same path as before
                                            {:type :auto})
          {:keys [chsk ch-recv send-fn state]} sente]
      (go-loop []
        (let [msg (<! chsk)
              {:keys [ring-req event ?reply-fn]} msg
              [_ msg] event]
          (put! client-receive-ch msg)))
      (merge this sente)))
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
    [:client-receive-ch]))
