(ns me.moocar.ftb500.game
  (:require [com.stuartsierra.component :as component]
            [datomic.api :as d]
            [me.moocar.ftb500.db :as db]
            [me.moocar.ftb500.bids :as bids]
            [me.moocar.ftb500.card :as card]
            [me.moocar.ftb500.deck :as deck]
            [me.moocar.ftb500.game-view :as game-view]
            [me.moocar.ftb500.kitty :as kitty]
            [me.moocar.ftb500.players :as players]
            [me.moocar.ftb500.pubsub2 :as pubsub]
            [me.moocar.ftb500.request :as request]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.tricks :as tricks])
  (:refer-clojure :exclude [find]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## API

(defn find
  [db ext-id]
  (db/find db :game/id ext-id))

(defn- make-game-tx
  [game-ext-id deck hands kitty player]
  {:pre [game-ext-id (coll? deck) (coll? hands) (coll? kitty)]}
  (let [game-id (d/tempid :db.part/user)
        seat-id (d/tempid :db.part/user)]
    (concat
     (map #(hash-map :db/id game-id
                                 :game.kitty/cards (:db/id %))
                      kitty)
     (seats/make-seat-tx game-id 0 (first hands) seat-id player)
     (mapcat #(seats/make-seat-tx game-id %1 %2)
             (range 1 4)
             (rest hands))
     [{:db/id game-id
       :game/id game-ext-id
       :game/first-seat seat-id
       :game/deck (:db/id deck)}])))

(defn add!
  [this client {:keys [player num-players]}]
  (request/wrap-bad-args-response
   [player (number? num-players)]
   (let [conn (:conn (:datomic this))
         db (d/db conn)
         deck (deck/find db num-players)
         deck-cards (shuffle (:deck/cards deck))
         game-ext-id (d/squuid)
         {:keys [hands kitty]} (deck/partition-hands deck-cards)
         game-tx (-> (make-game-tx game-ext-id deck hands kitty player)
                     (conj {:db/id (d/tempid :db.part/tx)
                            :tx/game-id game-ext-id
                            :action :action/create-game}))
         result @(d/transact conn game-tx)]
     #_(pubsub/register-client (:pubsub this) game-ext-id client)
     {:status :success
      :body {:game-id game-ext-id
             :cards (map card/ext-form (first hands))}})))

(defn join!
  [this client {:keys [game player]}]
  (request/wrap-bad-args-response
   [player game]
   (if-let [seat (seats/next-vacant game)]
     (let [conn (:conn (:datomic this))
           cards (:game.seat/cards seat)]
       @(d/transact conn
                    [{:db/id (d/tempid :db.part/tx)
                      :tx/game-id (:game/id game)
                      :action :action/join-game}
                     {:db/id (:db/id seat)
                      :game.seat/player (:db/id player)}])
       {:status :success
        :body {:cards (map card/ext-form cards)}})
     {:status :bad-args
      :body {:msg "No more seats left at this game"}})))

(defn bid!
  [this {:keys [game player bid]}]
  (request/wrap-bad-args-response
   [game player (keyword? bid)]
   (let [conn (:conn (:datomic this))]
     (if-let [seat (first (:game.seat/_player player))]
       (if-let [error (:error (bids/add! conn game seat bid))]
         {:status :bad-args
          :body error}
         {:status :success
          :body {}})
       {:status :bad-args
        :body {:msg "Could not find player's seat"
               :data {:player (:player/id player)}}}))))

(defn exchange-kitty!
  [this {:keys [game player cards]}]
  (request/wrap-bad-args-response
   [game player (coll? cards)]
   (if-let [seat (first (:game.seat/_player player))]
     (let [conn (:conn (:datomic this))
           db (d/db conn)
           card-entities (map #(card/find db %) cards)]
       (if-let [error (:error (kitty/exchange! conn seat card-entities))]
         {:status :bad-args
          :body error}
         {:status :success
          :body {}}))
     {:status :bad-args
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))

(defn play-card!
  [this {:keys [game player card]}]
  (request/wrap-bad-args-response
   [game player (map? card)]
   (if-let [seat (first (:game.seat/_player player))]
     (let [conn (:conn (:datomic this))
           db (d/db conn)
           card-entity (card/find db card)]
       (if-let [error (:error (tricks/add-play! conn seat card-entity))]
         {:status :bad-args
          :body error}
         {:status :success
          :body {}}))
     {:status :bad-args
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))

(defn view
  [this {:keys [game player]}]
  (request/wrap-bad-args-response
   [game player]
   (if-let [seat (first (:game.seat/_player player))]
     (let [conn (:conn (:datomic this))
           db (d/db conn)]
       {:status :success
        :body (game-view/view db game player)})
     {:status :bad-args
      :body {:msg "Could not find player's seat"
             :data {:player (:player/id player)}}})))

(defn new-games-component
  []
  (component/using {}
    [:datomic :pubsub]))
