(ns me.moocar.ftb500.client.transport.inline)

(defn- inline-send
  [this message callback]
  (let [{:keys [route msg]} message
        {:keys [user-id-atom client-receive-ch-atom]} this]
    (case route

      :login
      (let [user-id (:user-id msg)]
        (reset! user-id-atom user-id)
        (swap! client-receive-ch-atom assoc user-id client-recv-ch)
        (when callback
          (callback :success)))

      :logout
      (let [user-id (deref user-id-atom)]
        (swap! client-receive-ch-atom dissoc user-id)
        (reset! user-id-atom nil)
        (when callback
          (callback :success)))

      ;; Else it's a normal request, pass it to the server
      (let [user-id (eref user-id-atom)]
        (put! engine-receive-ch {:user-id user-id
                                 :msg msg
                                 :route route
                                 :callback callback})))))

(defrecord ClientInlineTransport [engine-receive-ch client-receive-ch-atom user-id-atom]
  ClientSender/Transport
  (send! [this message]
    (inline-send this message nil))
  (send! [this message timeout-ms callback]
    (inline-send this message callback)))

(defn new-client-inline-transport []
  (component/using (map->ClientInlineTransport {:user-id-atom (atom nil)})
    [:engine-receive-ch :client-receive-ch-atom]))
