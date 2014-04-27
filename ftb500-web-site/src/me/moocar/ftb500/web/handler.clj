(ns me.moocar.ftb500.web.handler
  (:require [clojure.java.io :as jio]
            [com.stuartsierra.component :as component]
            [me.moocar.log :as log]
            [ring.middleware.params :as params]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Request Handlers

(defn make-handler
  [component]
  (fn [request]
    (log/log (:log component) {:msg "Incoming HTTP Request"
                               :request request})
    {:status 200
     :body "Good"}))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Component

(defrecord HandlerComponent [log]
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (-> (make-handler this)
                   (wrap-resource "public")
                   (wrap-content-type)
                   (wrap-not-modified)
                   (->> (wrap-catch-error this)))))
  (stop [this]
    this))

(defn new-handler
  [config]
  (component/using (map->HandlerComponent {})
    [:log]))
