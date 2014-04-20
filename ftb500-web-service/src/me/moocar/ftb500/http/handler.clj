(ns me.moocar.ftb500.http.handler
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.handlers :as engine-handler]
            [me.moocar.ftb500.log :as log]
            [ring.middleware.params :as params]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Request Handlers

(def handler-lookup
  {[:post :create-player] :create-player
   [:post :create-game] :create-game
   [:post :join-game] :join-game
   [:post :bid] :bid
   [:post :exchange-kitty] :exchange-kitty
   [:post :play-card] :play-card
   [:get :game-view] :game-view})

(defn make-handler
  [component]
  (let [{:keys [engine-handler]} component]
    (fn [request]
      (let [{:keys [uri request-method body]} request
            action-name (subs uri 1)
            action (get handler-lookup [request-method (keyword action-name)])
            args (edn/read-string (slurp (jio/reader body)))
            new-request {:action action
                         :args args}]
        (log/log (:log component) {:msg "Incoming HTTP Request"
                                   :request new-request})
        (engine-handler/handle-request engine-handler new-request)))))

(defn wrap-catch-error
  [component handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (log/log (:log component) {:msg "Error in HTTP Handler"
                                   :ex t})
        {:status 500
         :body "Internal Server Error"}))))

(defn wrap-edn-response
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (update-in [:body] pr-str)
          (update-in [:headers] assoc "Content-Type" "application/edn")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Component

(defrecord HandlerComponent [engine-handler log]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (-> (make-handler this)
                   (wrap-edn-response)
                   (->> (wrap-catch-error this)))))
  (stop [this]
    this))

(defn new-handler
  [config]
  (component/using (map->HandlerComponent {})
    [:engine-handler :log]))
