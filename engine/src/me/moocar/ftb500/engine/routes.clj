(ns me.moocar.ftb500.engine.routes
  (:require [datomic.api :as d]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.datomic :as db]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.transport.user-store :as user-store]
            [me.moocar.ftb500.engine.user :as user-lookup]
            [me.moocar.lang :refer [uuid]]
            [me.moocar.log :as log]))

(defprotocol Route
  (serve [this db request]))

(defn route
  [this
   {:keys [conn route body request-id logged-in-user-id] :as request}]
  {:pre [(keyword? route)
         (map? conn)]}
  (let [route-ns-keyword (keyword "routes" (name route))]
    (log/log (:log this) (format "%8.8s %-10.10s %s"
                                 (if logged-in-user-id
                                   (str logged-in-user-id)
                                   "ANON")
                                 route
                                 body))
    (if-let [server (get this route-ns-keyword)]
      (let [{:keys [datomic]} this
            db-conn (:conn datomic)
            db (d/db db-conn)]
        (when-let [response (serve server db request)]
          (when (or (keyword? response) (not= :success (first response)))
            (log/log (:log this) {:ERROR response
                                  :request body}))
          (assoc request :response response)))
      (assoc request :response [:ftb500/no-route {:route route}]))))

(defn promote-route
  [{:keys [body] :as request}]
  (assoc request
         :body (:body body)
         :route (:route body)))

(defn add-logged-in-user-id
  [{:keys [user-store] :as handler}]
  (fn [{:keys [conn] :as request}]
    (if-let [user-id (user-store/find-user user-store conn)]
      (assoc request :logged-in-user-id user-id)
      request)))

(defrecord EngineHandler [router user-store]
  component/Lifecycle
  (start [this]
    (assoc this
           :xf (comp (map promote-route)
                     (map (add-logged-in-user-id this))
                     (keep #(route router %)))))
  (stop [this]
    this))

(defn new-engine-handler []
  (component/using (map->EngineHandler {})
    [:router :user-store]))

(defn new-router []
  (component/using {}
    [:log
     :datomic
     :routes/add-game
     :routes/bid
     :routes/game-info
     :routes/join-game
     :routes/login
     :routes/logout
     :routes/signup
     :routes/exchange-kitty
     :routes/play-card]))
