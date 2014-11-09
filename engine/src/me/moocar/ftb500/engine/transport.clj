(ns me.moocar.ftb500.engine.transport
  (:require [clojure.core.async :as async :refer [go-loop <!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.routes :as router]
            [me.moocar.log :as log]))

(defprotocol EngineTransport
  (-send! [this user-id msg]))

(defrecord EngineMultiTransport [transports]
  EngineTransport
  (-send! [this user-id msg]
    (doseq [transport-k transports]
      (let [transport (get this transport-k)]
        (-send! transport user-id msg)))))

(defn new-engine-multi-transport [transports]
  (component/using (map->EngineMultiTransport {:transports transports})
    transports))

(defn send!
  "Sends the message to the user. The user can be connected by many
  underlying transports and all will receive the message. E.g a user
  might be connected by ssh and sente"
  [transport user-id msg]
  (-send! transport user-id msg))

(defn wait-for-in-flight [this]
  (log/log (:log this) "Starting to wait for in flight")
  (go-loop []
    (if (empty? @(:in-flight this))
      (do (log/log (:log this) "Finished waiting for in flight")
          true)
      (do (<! (async/timeout 300))
          (recur)))))

(defn tracked-callback [callback in-flight]
  (swap! in-flight conj callback)
  (fn [& args]
    (swap! in-flight disj callback)
    (apply callback args)))

(defrecord ServerListener [log router receive-ch run-loop in-flight shutting-down?]
  component/Lifecycle
  (start [this]
    (if receive-ch
      this
      (let [receive-ch (async/chan)
            in-flight (atom #{})
            run-loop
            (go-loop []
              (if-let [full-msg (<! receive-ch)]
                (do
                  (try
                    (router/route router
                                  (cond-> full-msg
                                          (:callback full-msg)
                                          (update-in [:callback] tracked-callback in-flight)))
                    (catch Throwable t
                      (log/log log t)))
                  (recur))
                (do (log/log log "Run loop finished")
                    :finished)))]
        (assoc this
          :run-loop run-loop
          :receive-ch receive-ch
          :in-flight in-flight))))
  (stop [this]
    (if (and receive-ch (not @shutting-down?))
      (do
        (try
          (log/log log "Shutting down Server Listener")
          (reset! shutting-down? true)
          (async/close! receive-ch)
          (if-not (first (async/alts!! [(async/take 2 (async/merge [run-loop (wait-for-in-flight this)]))
                                        (async/timeout 1000)]))
            (log/log log "Requests took too long to shutdown")
            (log/log log "Shutdown Success"))
          (assoc this :receive-ch nil)
          (finally
            (reset! shutting-down? false))))
      this)))

(defn new-server-listener []
  (component/using (map->ServerListener {:shutting-down? (atom false)})
    [:log :router]))
