(ns me.moocar.ftb500.engine.transport)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## User Management

(defn new-user
  []
  (component/using {:user-transports {}}
    [:datomic]))

(defn add-transport!
  [this user-id transport]
  (swap! transports
         update-in
         [user-id]
         (fn [transports]
           (if (nil? transports)
             #{transport}
             (conj transports transport)))))

(defn del-transport!
  [this user-id transport]
  (swap! transports
         update-in
         [user-id]
         disj
         transport))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; My API:

(defprotocol Transport
  (send! [this user msg]))

(defprotocol ClientTransport
  (send! [this user-id] [this user-id timeout-ms callback]))

(defn send!
  "Sends the message to the user. The user can be connected by many
  underlying transports and all will receive the message. E.g a user
  might be connected by ssh and sente"
  [transport user msg]
  (let [{:keys [transports]} user]
    (doseq [transport transports]
      (let [{:keys [transport user]} transport]
        (transport-protocol/send! transport user msg)))))

(defn start-listen-loop
  [this]
  (let [{:keys [engine-receive-ch]} this]
    (go-loop [msg (<! engine-receive-ch)]
      (let [{:keys [user msg]} msg]
        ;; Do stuff
        ))))

;; client

(send! client-transport
       {:route :login
        :msg {:user-id "blah"}})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Inline Impl

(defrecord InlineTransport [engine-receive-ch client-recv-ch user-lookup user-atom]
  client-transport/Transport
  (send! [this message]
    (let [{:keys [route msg]} message]
      (case route

        :login
        (let [user-id (:user-id msg)]
          (user/add-transport! user user-id this)
          )

        :logout
        (let [user-id (:user-id msg)]
          (user/del-transport! user user-id this))

        ;; Else it's a normal request, pass it to the server
        (let [user (user/lookup user-lookup )]
         (put! engine-receive-ch {:user user
                                  :msg msg
                                  :route route})))))
  transport/Transport
  (send! [this inline-user msg]
    (put! (:receive-ch inline-user) msg)))

(defn new-inline-transport
  []
  (component/using (map->InlineTransport {})
    [:user :engine-receive-ch :client-recv-ch]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Sente Impl

(defrecord SenteTransport [engine-receive-ch user-lookup session-store]

  component/Lifecycle
  (start [this]
    (let [sente (sente/make-channel-socket! {})
          {:keys [ch-recv]} sente]
      (go-loop []
        (let [event-msg (<! ch-recv)
              {:keys [ring-req event ?reply-fn]} event-msg
              [ev-id ev-data] event
              route ev-id
              {:keys [session]} ring-req
              {:keys [uid]} session]
          (case route

            :chsk/uidport-close
            (do (user/del-transport! user uid this)
                (>! engine-receive-ch {:user user
                                       :msg ev-data}))

            :login
            (let [user-id (:user-id ev-data)]
              (user/add-transport! user user-id this)
              (session-store/write-session session-store :uid user-id))

            :logout
            (let [user-id (:user-id ev-data)]
              (user/del-transport! user user-id this)
              (session-store/delete-session session-store :uid))

            ;; Else it's a normal request. Pass it to the server
            (let [user (user/lookup user-lookup uid)]
              (>! engine-receive-ch {:user user
                                     :msg ev-data
                                     :route route})))))
      (merge this sente)))
  (stop [this]
    this)

  transport/Transport
  (send! [this sente-user msg]
    (let [{:keys [send-fn]} this
          {:keys [user-id]} sente-user]
      (send-fn user-id msg))))

(defn new-sente-transport []
  (component/using (map->SenteTransport {})
    [:user :engine-receive-ch]))

(defrecord HttpHandler [session-store]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler
      (session/wrap-session
       (fn [ring-request]
         (let [{:keys [request-method uri]} ring-request]
           (case [request-method uri]
             [:get "/chsk"] (sente/ring-ajax-get-or-ws-handshake ring-request)
             [:get "/chsk"] (sente/ring-ajax-post ring-request)
             {:status 400
              :body "Not found"})))
       {:store session-store}))))

(defn new-http-handler []
  (components/using (map->HttpHandler {})
    [:user :session-store]))

;; Client

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
  client-transport/Transport
  (send! [this msg]
    (let [{:keys [route]} msg]
      (assert (keyword? route))
      (send-fn [route (dissoc msg :route)])))
  (send! [this msg timeout-ms callback]
    (let [{:keys [route]} msg]
      (assert (keyword? route))
      (send-fn [route (dissoc msg :route)] timeout-ms callback))))
