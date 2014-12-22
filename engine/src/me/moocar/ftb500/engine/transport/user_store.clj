(ns me.moocar.ftb500.engine.transport.user-store)

(defrecord DefaultUserStore [db])

(defn default-user-store []
  {:db (atom {:conn->user-id {}
              :user-id->conns {}})})

(defn find-user [{:keys [db]} conn]
  (get-in @db [:conn->user-id conn]))

(defn user-conns [{:keys [db]} user-id]
  (get-in @db [:user-id->conns user-id]))

(defn write [{:keys [db]} conn user-id]
  (swap! db
         (fn [db]
           (-> db
               (assoc-in [:conn->user-id conn] user-id)
               (update-in [:user-id->conns user-id]
                          (fn [conns]
                            (if conns
                              (conj conns conn)
                              #{conn})))))))

(defn delete [{:keys [db]} conn]
  (swap! db
         (fn [db]
           (let [user-id (get-in db [:conn->user-id conn])]
             (-> db
                 (update-in [:user-id->conns user-id] disj conn)
                 (update :conn->user-id dissoc conn))))))
