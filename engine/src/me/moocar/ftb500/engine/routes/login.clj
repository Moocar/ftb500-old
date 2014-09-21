(ns me.moocar.ftb500.engine.routes.login
  (:require [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]))

(defn uuid?
  [thing]
  (instance? java.util.UUID thing))

(defrecord Login [datomic user-store]
  routes/Route
  (serve [this db request]
    (let [{:keys [body client-id callback]} request]
      (if-let [user-id (:user-id body)]
        (if (uuid? user-id)
          (do
            (user-store/write user-store client-id user-id)
            (callback :success))
          (callback [:error :user-id-must-be-uuid]))
        (callback [:error {:expected :user-id}])))))

(defrecord Logout [datomic user-store]
  routes/Route
  (serve [this db request]
    (let [{:keys [client-id]} request]
      (user-store/delete user-store client-id)
      (when-let [callback (:callback request)]
        (callback :success)))))
