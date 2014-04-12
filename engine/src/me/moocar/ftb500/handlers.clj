(ns me.moocar.ftb500.handlers
  (:require [datomic.api :as d]
            [me.moocar.ftb500.log :as log]
            [me.moocar.ftb500.players :as players]))

(defn create-player
  [this request]
  (let [conn (d/db (:conn (:db this)))
        player-name (:player-name request)
        player-ext-id (d/squuid)]
    (assert (string? player-name))
    @(players/add! conn player-ext-id player-name)
    {:status :success
     :body {:player-id player-ext-id}}))

(defn make-action-handler-lookup
  []
  {:create-player create-player})

(defn find-player-id
  [db player-id]
  (or (players/find player-id)
      (throw (ex-info "Player not found" {:player-id player-id}))))

(defn handle-request
  [this request]
  (let [action-handler-lookup (:action-handler-lookup this)
        {:keys [action args player-id]} request
        db (d/db (:conn (:db this)))]
    (log/log (:log this) {:request request})
    (let [response ((get action-handler-lookup action)
                    this
                    (assoc request
                      :player player))]
      (log/log (:log this) {:response response}))))

(defrecord Handler [action-handler-lookup])
