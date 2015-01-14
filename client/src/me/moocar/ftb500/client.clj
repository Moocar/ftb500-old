(ns me.moocar.ftb500.client
  (:require [clojure.core.async :as async :refer [<!]]
            [me.moocar.async :as moo-async :refer [<? go-try]]
            [me.moocar.ftb500.client.schema :refer [client?]]
            [me.moocar.ftb500.schema :as schema :refer [seat? ext-card?]]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.lang :refer [uuid?]]
            [me.moocar.log :as log]))

(defn log [client msg]
  (log/log (:log client) msg))

(defn send!
  [client route msg]
  {:pre [(:transport client)]}
  (let [send-ch (:send-ch (:conn (:transport client)))]
    (go-try
     (let [response (<? (moo-async/request send-ch
                                           {:route route
                                            :body msg}))
           {:keys [body]} response]
       (if (or (keyword? body) (not= :success (first body)))
         (do (log client {:ERROR body})
             (throw (ex-info "Error in Send"
                             {:error body
                              :route route
                              :request msg})))
         body)))))

(defn game-send!
  [client route msg]
  (let [new-msg (assoc msg
                  :seat/id (:seat/id (:seat client)))]
    (log client {:route route :msg new-msg})
    (send! client route new-msg)))

(defn touch-game
  [game]
  (update-in game
             [:game/deck :deck/cards]
             #(map schema/touch-card %)))

(defn game-info
  [this game-id]
  (go-try
   (-> (send! this :game-info {:game-id game-id})
       <?
       second
       touch-game)))

(defn join-game
  "Attempts to join the game in [:game :game/id]. If player ha already
  joined, the seat they are assigned to is immediately returned.
  Otherwise, an available seat is selected and an attempt to join the
  game is made. If successful, the seat is returned, otherwise a new
  seat is selected and tried again, in a loop.

  If a seat is specified, an attempt will be made to join that seat.
  If someone else has taken it, nil is returned"

  ;; seat specified
  ([{:keys [player game route-pub-ch] :as client}
    seat]
   {:pre [(client? client) (seat? seat)]}
   (let [game-id (:game/id game)]
     (go-try
      (if-let [assigned (seats/find-assigned game player)]
        (if-not (seats/player= player (:seat/player assigned))
          (throw (ex-info "you've already been assigned to seat"
                          {:seat assigned}))
          assigned)
        (do
          (<? (game-send! (assoc client :seat seat)
                          :join-game {:game/id game-id}))
          (assoc seat :user/id (:user/id player)))))))

  ;; seat not specified
  ([{:keys [player game] :as client}]
   {:pre [(client? client)]}
   (let [game-id (:game/id game)]
     (go-try
      (loop [game game]
        (if-let [seat (seats/find-available game)]
          (let [seat (<! (join-game client seat))]
            (if-let [error (ex-data seat)]
              (if (= [:seat-taken] (:error error))
                (recur (<? (game-info client game-id)))
                (throw seat))
              seat))
          (throw (ex-info "No more seats available"))))))))

(defn get-deal-cards
  "Takes the deal-cards message and retrieves the first-seat and dealt
  hand and associates them back into the client"
  [client deal-cards]
  {:pre [(client? client)]}
  (let [{:keys [game]} client
        {:keys [body]} deal-cards
        {:keys [seat/cards]} body
        _ (assert (every? ext-card? cards))
        first-seat (seats/find (:game/first-seat body) game)
        hand (set (map schema/touch-card cards))]
    (-> client
        (assoc-in [:seat :seat/cards] hand)
        (assoc-in [:game :game/first-seat] first-seat))))

(defn ready-game
  "Initiates the ai map with the basic game information"
  [client game-id]
  {:pre [(:transport client)
         (uuid? game-id)]}
  (let [{:keys [route-pub-ch]} client]
    (go-try
     (-> client
         (assoc :game (<? (game-info client game-id)))
         (as-> client
               (assoc client :game/num-players (count (:game/seats (:game client)))))))))

(defn wait-on-joins
  "Waits for all players to join the game and returns all the seats"
  [join-game-ch client]
  {:pre [(client? client)]}
  (go-try
   (->> join-game-ch
        (async/take (:game/num-players client))
        (async/into [])
        (<?)
        (map :body)
        (sort-by :seat/position))))

(defn join-game-and-wait-for-others
  "Joins the game in ai and waits for all players to join the game,
  and for the hand to be dealt"
  [client joined-seat-ch]
  {:pre [(client? client)]}
  (let [{:keys [route-pub-ch]} client
        join-game-ch (async/chan)
        deal-cards-ch (async/chan)]
    (async/sub route-pub-ch :join-game join-game-ch)
    (async/sub route-pub-ch :deal-cards deal-cards-ch)
    (go-try
     (as-> client client
       (assoc client :seat (<? joined-seat-ch))
       (assoc-in client [:game :game/seats] (<? (wait-on-joins join-game-ch client)))
       (get-deal-cards client (<? deal-cards-ch))))))
