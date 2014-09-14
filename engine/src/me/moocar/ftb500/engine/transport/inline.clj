(ns me.moocar.ftb500.engine.transport.inline)

(defrecord ServerInlineTransport [client-receive-ch-atom]
  transport/Transport
  (send! [this user-id msg]
    (let [client-receive-ch (get (deref client-receive-ch-atom) user-id)]
      (put! client-receive-ch msg))))

(defn new-server-inline-transport []
  (component/using (map->ServerInlineTransport {})
    [:client-receive-ch-atom]))
