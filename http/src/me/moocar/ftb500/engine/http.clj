(ns me.moocar.ftb500.engine.http
  (:require [com.stuartsierra.component :as component]
            [ring.adapter.jetty9 :as jetty9]
            [ring.middleware.session :as session]))

(defrecord HttpHandler [session-store handler]
  component/Lifecycle
  (start [this]
    (if handler
      this
      (assoc this
        :handler
        (session/wrap-session
         (fn [ring-request]
           {:status 400
            :body "Not found"})
         {:store session-store}))))
  (stop [this]
    (assoc this :handler nil)))

(defn new-http-handler [config]
  (component/using (map->HttpHandler {})
    [:session-store]))

(defrecord HttpServer [port websockets server http-handler websocket-handler]
  component/Lifecycle
  (start [this]
    (if server
      this
      (let [{websocket-path :path} websockets
            config {:port port
                    :join? false}
            options (merge config
                           {:websockets {websocket-path websocket-handler}})
            server (jetty9/run-jetty (:handler http-handler)
                                     options)]
        (assoc this :server server))))
  (stop [this]
    (if server
      (do
        (.stop server)
        (assoc this :server nil))
      this)))

(defn new-http-server [config]
  (component/using (map->HttpServer (get-in config [:engine :http :server]))
    {:websocket-handler :jetty9-websocket
     :http-handler :http-handler}))
