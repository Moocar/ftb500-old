(ns me.moocar.ftb500.engine.routes.login
  (:require [datomic.api :as d]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]
            [me.moocar.lang :refer [uuid?]]))

(defn exists?
  [db user-id]
  (-> '[:find ?user
        :in $ ?user-id
        :where [?user :user/id ?user-id]]
      (d/q db user-id)
      ffirst))

(defrecord Login [user-store]
  routes/Route
  (serve [this db request]
    (let [{:keys [body conn]} request
          {:keys [user-id]} body]
      (cond (not user-id) [:user-id-required]
            (not (uuid? user-id)) [:user-id-must-be-uuid]
            :else
            (if (exists? db user-id)
              (do (user-store/write user-store conn user-id)
                  [:success])
              [:user-not-found])))))

(defrecord Logout [user-store]
  routes/Route
  (serve [this db request]
    (let [{:keys [conn]} request]
      (user-store/delete user-store conn)
      [:success])))

(defrecord Signup [datomic]
  routes/Route
  (serve [this db request]
    (let [conn (:conn datomic)
          {:keys [body]} request
          {:keys [user-id]} body]
      (cond
        (not user-id) [:user-id-required]
        (not (uuid? user-id)) [:user-id-must-be-uuid]
        :else
        (let [tx [[:db/add (d/tempid :db.part/user)
                   :user/id user-id]]]
          @(d/transact conn tx)
          [:success])))))
