(ns me.moocar.ftb500.engine.routes.login
  (:require [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]))

(defrecord Login [datomic user-store]
  routes/Route
  (serve [this db request]
    (let [{:keys [body client-id callback]} request]
      (if-let [user-id (:user-id body)]
        (do
          (user-store/write user-store client-id user-id)
          (callback :success))
        (callback [:error {:expected :user-id}])))))

(defrecord Logout [datomic user-store]
  routes/Route
  (serve [this db request]
    (let [{:keys [client-id]} request]
      (user-store/delete user-store client-id)
      (when-let [callback (:callback request)]
        (callback :success)))))
