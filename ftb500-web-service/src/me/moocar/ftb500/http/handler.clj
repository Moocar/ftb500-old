(ns me.moocar.ftb500.http.handler
  (:require [com.stuartsierra.component :as component]))

(defn default-handler
  [request]
  {:status 404
   :body {:msg "No Service Found"}})

(defn make-handler
  [component]
  (fn [request]
    (default-handler request)))

(defn wrap-catch-error
  [this handler]
  (fn [request]
    (try
      (handler request)
      (catch Throwable t
        (println "got error" t)
        (.printStackTrace t)
        {:status 500
         :body {:msg "Internal Server Error"}}))))

(defn wrap-edn-response
  [handler]
  (fn [request]
    (let [response (handler request)]
      (-> response
          (update-in [:body] pr-str)
          (update-in [:headers] assoc "Content-Type" "application/edn")))))

(defrecord HandlerComponent []
  component/Lifecycle
  (start [this]
    (assoc this
      :handler (-> (make-handler this)
                   (->> (wrap-catch-error this))
                   (wrap-edn-response))))
  (stop [this]
    this))

(defn new-handler
  [config]
  (map->HandlerComponent {}))
