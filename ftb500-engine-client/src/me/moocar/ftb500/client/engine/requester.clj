(ns me.moocar.ftb500.client.engine.requester
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.handlers :as handler]
            [me.moocar.ftb500.pubsub2 :as pubsub]
            [me.moocar.ftb500.protocols :as protocols]
            [me.moocar.ftb500.client.transport :as transport]
            [me.moocar.ftb500.clients :as clients]))

(defrecord Requester [handler pubsub clients]

  protocols/Requester
  (send-request [this request]
    (handler/handle-request handler request))

  protocols/Subscriber
  (subscribe [this game-id ch]
    (pubsub/register-client pubsub game-id ch))

  transport/Transporter
  (register [this client-id request-ch response-ch]
    (clients/register clients client-id request-ch response-ch)))

(defn new-requester
  []
  (component/using (map->Requester {})
    [:handler :pubsub :clients]))
