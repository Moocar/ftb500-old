(ns me.moocar.ftb500.engine.http
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.session :as session]
            [taoensso.sente :as sente]))

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
  (component/using (map->HttpHandler {})
    [:session-store]))
