(ns me.moocar.ftb500.http.handler
  (:require [clojure.edn :as edn]
            [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.handlers :as engine-handler]
            [ring.middleware.params :as params]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Request Handlers

(def handler-lookup
  {[:post :create-player] :create-player
   [:post :create-game] :create-game
   [:post :join-game] :join-game
   [:post :bid] :bid
   [:post :exchange-kitty] :exchange-kitty})

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
        (println "new request" new-request)
        (engine-handler/handle-request engine-handler new-request)))))

(defn wrap-catch-error
  [handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (println "got error" t)
        (.printStackTrace t)
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
;; ## Websocket Handlers

(defn make-websocket-handlers
  []
  {:on-connect (fn [ws] (println "connect" ws))
   :on-error (fn [ws e] (println "error" ws e))
   :on-close (fn [ws] (println "close" ws))
   :on-text (fn [ws text] (println "text" ws text))
   :on-bytes (fn [ws bytes offset len]
               (println "bytes" ws bytes offset len))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Component

(defrecord HandlerComponent [engine-handler]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (-> (make-handler this)
                   (wrap-edn-response)
                   (wrap-catch-error))
      :websocket-handler (make-websocket-handlers)))
  (stop [this]
    this))

(defn new-handler
  [config]
  (component/using (map->HandlerComponent {})
    [:engine-handler]))
