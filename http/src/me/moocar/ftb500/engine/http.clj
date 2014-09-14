(ns me.moocar.ftb500.engine.http
  (:require [com.stuartsierra.component :as component]
            [ring.middleware.session :as session]))

(defrecord HttpHandler [session-store sente-transport handler]
  component/Lifecycle
  (start [this]
    (if handler
      this
      (let [{:keys [ajax-post-fn ajax-get-or-ws-handshake-fn]} sente-transport]
        (assoc this
          :handler
          (session/wrap-session
           (fn [ring-request]
             (let [{:keys [request-method uri]} ring-request]
               (case [request-method uri]
                 [:get "/chsk"] (ajax-get-or-ws-handshake-fn ring-request)
                 [:post "/chsk"] (ajax-post-fn ring-request)
                 {:status 400
                  :body "Not found"})))
           {:store session-store})))))
  (stop [this]
    (assoc this :handler nil)))

(defn new-http-handler []
  (component/using (map->HttpHandler {})
    [:session-store :sente-transport]))
