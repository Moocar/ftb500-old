(ns me.moocar.ftb500.client.ai
  (:require [clojure.core.async :as async :refer [go <! put! go-loop]]
            [clojure.set :refer [rename-keys]]
            [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.ai.transport :refer [game-send! send!]]
            [me.moocar.ftb500.client.ai.bids :as bids]
            [me.moocar.ftb500.client.ai.tricks :as tricks]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.schema :as schema
             :refer [game? seat? bid? player? uuid? ext-card? card?]]
            [me.moocar.lang :refer [uuid]]
            [me.moocar.log :as log]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn start
  [this]
  (if (:player this)
    this
    (let [{:keys [receive-ch]} this
          mult (async/mult receive-ch)
          tapped-ch (async/chan)
          route-pub-ch (async/pub tapped-ch :route)
          user-id (uuid)]
      (async/tap mult tapped-ch)
      (go-try
        (and (<? (send! this :signup {:user-id user-id}))
             (<? (send! this :login {:user-id user-id})))
        (assoc this
          :route-pub-ch route-pub-ch
          :player {:player/name "Anthony"
                   :user/id user-id})))))

(defn stop
  [ai]
  {:pre [(ai? ai)]}
  (go-try
   (log ai "Stopping ai client")
   (<? (send! ai :logout {}))
   (log ai "Stopped ai client")
   ai))

(defn touch-game
  [game]
  (update-in game [:game/deck :deck/cards] #(map schema/touch-card %)))

(defn game-info
  [this game-id]
  (go-try (touch-game (second (<? (send! this :game-info {:game-id game-id}))))))

(defn join-game
  "Attempts to join the game in [:game :game/id]. If player is already
  joined, the seat they are assigned to is immediately returned.
  Otherwise, an available seat is selected and an attempt to join the
  game is made. If successful, the seat is returned, otherwise a new
  seat is selected and tried again, in a loop"
  [{:keys [player game] :as ai}]
  {:pre [(ai? ai)]}
  (let [game-id (:game/id game)]
    (go-try
      (loop [game game]
        (or (seats/find-assigned game player)
            (if-let [seat (seats/find-available game)]
              (do
                (<! (game-send! (assoc ai :seat seat)
                                :join-game {:game/id game-id}))
                (recur (<? (game-info ai game-id))))
              (throw (ex-info "No more seats available"))))))))

(defn get-deal-cards
  "Takes the deal-cards message and retrieves the first-seat and dealt
  hand and associates them back into the ai map"
  [ai deal-cards]
  {:pre [(ai? ai)]}
  (let [{:keys [game]} ai
        {:keys [body]} deal-cards
        {:keys [cards]} body
        _ (assert (every? ext-card? cards))
        first-seat (seats/find (:game/first-seat body) game)
        hand (set (map schema/touch-card cards))]
    (-> ai
        (assoc :hand hand)
        (assoc-in [:game :game/first-seat] first-seat))))

(defn ready-game
  "Initiates the ai map with the basic game information"
  [ai game-id]
  {:pre [(uuid? game-id)]}
  (let [{:keys [route-pub-ch]} ai]
    (go-try
     (-> ai
         (assoc :game (<? (game-info ai game-id)))
         (as-> ai
               (assoc ai :game/num-players (count (:game/seats (:game ai)))))))))

(defn wait-on-joins
  "Waits for all players to join the game and returns all the seats"
  [join-game-ch ai]
  {:pre [(ai? ai)]}
  (go-try 
   (->> join-game-ch
        (async/take (:game/num-players ai))
        (async/into [])
        (<?)
        (map :body)
        (sort-by :seat/position))))

(defn join-game-and-wait-for-others
  "Joins the game in ai and waits for all players to join the game,
  and for the hand to be dealt"
  [ai]
  {:pre [(ai? ai)]}
  (let [{:keys [route-pub-ch]} ai
        join-game-ch (async/chan)
        deal-cards-ch (async/chan)]
    (async/sub route-pub-ch :join-game join-game-ch)
    (async/sub route-pub-ch :deal-cards deal-cards-ch)
    (go-try
     (as-> ai ai
           (assoc ai :seat (<? (join-game ai)))
           (assoc-in ai [:game :game/seats] (<? (wait-on-joins join-game-ch ai)))
           (get-deal-cards ai (<? deal-cards-ch))))))

(defn start-playing
  "Join a game, wait for all players to join, run the bidding round,
  and then the tricks roundq"
  [ai game-id]
  (go-try
   (-> (ready-game ai game-id)
       <?
       (join-game-and-wait-for-others)
       <?
       (bids/start)
       <?
       (tricks/start)
       <?)))

(defn new-client-ai
  [this]
  (let [{:keys [log client-transport]} this
        {:keys [listener]} client-transport
        {:keys [mult]} listener
        receive-ch (async/chan)]
    (async/tap mult receive-ch)
    (merge this {:receive-ch receive-ch})))
