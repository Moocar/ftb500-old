(ns me.moocar.ftb500.engine.routes.play-card
  (:require [datomic.api :as d]
            [me.moocar.ftb500.bid :as bids]
            [me.moocar.ftb500.engine.card :as card]
            [me.moocar.ftb500.engine.datomic :as datomic]
            [me.moocar.ftb500.engine.datomic.schema :as db-schema]
            [me.moocar.ftb500.engine.routes :as routes]
            [me.moocar.ftb500.engine.transport :as transport]
            [me.moocar.ftb500.engine.tx-handler :as tx-handler]
            [me.moocar.ftb500.schema :as schema
             :refer [uuid? card? ext-card? trick? play? seat? suit? trick-game?]]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]
            [me.moocar.ftb500.trick :as trick]
            [me.moocar.log :as log]))

(defn kitty-exchanged?
  [db game]
  {:pre [(trick-game? game)]}
  (let [num-players (count (:game/seats game))]
    (-> '[:find ?cards ?tx ?added
          :in $ ?game
          :where [?game :game.kitty/cards ?cards ?tx ?added]]
        (d/q (d/history db) (:db/id game))
        (count)
        (not= 3))))

(defn could-have-followed-lead-suit?
  "Returns true if the seat did not follow suit"
  [game seat card]
  {:pre [(trick-game? game)
         (seat? seat)
         (card? card)]}
  (let [{:keys [game/tricks]} game]
    (if (empty? tricks)
      false
      (let [trick (last tricks)]
        (when-not (trick/finished? game trick)
          (let [{:keys [trick/plays]} trick
                leading-play (first plays)
                leading-suit (trick/find-leading-suit trick)]
            (and (seats/get-follow-cards seat leading-suit)
                 (not= (:card/suit card) leading-suit))))))))

(defn implementation [this db request]
  (let [{:keys [datomic log]} this
        {:keys [logged-in-user-id body callback]} request
        {card :trick.play/card seat-id :seat/id} body]
    (callback
     (cond

      ;; Check basic inputs

      (not logged-in-user-id) :must-be-logged-in
      (not seat-id) :seat-id-required
      (not (uuid? seat-id)) :seat-id-must-be-uuid
      (not (ext-card? card)) :card-is-not-ext-card

      :else ;; Load entities

      (let [card (card/find db card)
            seat (datomic/find db :seat/id seat-id)
            game (db-schema/touch-game (first (:game/_seats seat)))
            {:keys [game/seats game/tricks game/deck]} game
            last-trick (last tricks)
            game (trick/update-contract game)]

        (cond
         
         ;; Make sure entities exist

         (not seat) :seat-not-found
         (not card) :card-not-found
         (not (card? card)) :card-is-not-card
         
         ;; Validations

         (not (kitty-exchanged? db game)) :kitty-not-exchanged-yet

         (not (seat= seat (trick/next-seat game))) :not-your-go

         (not (seats/has-card? seat card)) :you-dont-own-that-card

         (could-have-followed-lead-suit? game seat card) :could-have-followed-suit

         :main

         (let [new-trick? (or (empty? tricks)
                              (trick/finished? game last-trick))
               trick-id (if new-trick?
                          (d/tempid :db.part/user)
                          (:db/id last-trick))
               trick-tx (when new-trick?
                          [{:db/id (:db/id game)
                            :game/tricks trick-id}])
               play-id (d/tempid :db.part/user)
               play-tx [{:db/id trick-id
                         :trick/plays play-id}
                        {:db/id play-id
                         :trick.play/seat (:db/id seat)
                         :trick.play/card (:db/id card)}
                        [:db/retract (:db/id seat)
                         :seat/cards (:db/id card)]]
               tx (concat trick-tx play-tx)]
           @(datomic/transact-action datomic tx (:game/id game) :action/play-card)
           [:success])))))))

(defrecord PlayCard [datomic log]
  routes/Route
  (serve [this db request]
    (implementation this db request)))

(defrecord PlayCardTxHandler [engine-transport log]
  tx-handler/TxHandler
  (handle [this user-ids tx]
    (let [db (:db-after tx)
          trick (datomic/get-attr tx :trick.play/card)
          {:keys [trick.play/card trick.play/seat]} trick
          msg {:route :play-card
               :body {:trick.play/card (db-schema/card-ext-form card)
                      :trick.play/seat {:seat/id (:seat/id seat)}}}]
      (doseq [user-id user-ids]
        (transport/send! engine-transport user-id msg)))))
