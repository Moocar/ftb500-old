(ns me.moocar.ftb500.client.ai
  (:require [clojure.core.async :as async :refer [go <! put! go-loop]]
            [clojure.set :refer [rename-keys]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.transport :as transport]
            [me.moocar.ftb500.client.ai.bids :as bids]
            [me.moocar.ftb500.client.ai.tricks :as tricks]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.schema :as schema
             :refer [game? seat? bid? player? uuid? ext-card? card?]]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn uuid []
  (java.util.UUID/randomUUID))

(defn send!
  ([this route msg dont-send]
     (client/send! this route msg dont-send))
  ([this route msg]
     (go
       (let [response (<! (client/send! this route msg))]
         (if (keyword? response)
           (let [error (ex-info "Error in Send"
                                {:error response
                                 :route route
                                 :request msg})]
             (log this error)
             error)
           (if (instance? Throwable response)
             (do (log this response)
                 nil)
             response))))))

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
      (go
        (and (<! (send! this :signup {:user-id user-id}))
             (<! (send! this :login {:user-id user-id})))
        (assoc this
          :route-pub-ch route-pub-ch
          :player {:player/name "Anthony"
                   :user/id user-id})))))

(defn stop
  [ai]
  {:pre [(ai? ai)]}
  (go
    (log ai "Stopping ai client")
    (<! (send! ai :logout {}))
    (log ai "Stopped ai client")
    ai))

(defn touch-game
  [game]
  (-> game
      (update-in [:game/deck :deck/cards] #(map schema/touch-card %))))

(defn game-info
  [this game-id]
  (go (touch-game (second (<! (send! this :game-info {:game-id game-id}))))))

(defn find-players-seat [player seats]
  (first (filter #(seats/taken-by? % player) seats)))

(defn find-available-seat [seats]
  (first (remove seats/taken? seats)))

(defn join-game
  [{:keys [player game] :as ai}]
  {:pre [(ai? ai)]}
  (let [game-id (:game/id game)]
    (go-loop [{:keys [game/seats]} (:game ai)]
      (or (find-players-seat player seats)
          (when-let [seat (find-available-seat seats)]
            (do (<! (send! ai :join-game {:game/id game-id
                                          :seat/id (:seat/id seat)}))
                (recur (<! (game-info ai game-id)))))))))

(defn find-seat [seats seat-id]
  (first (filter #(= seat-id (:seat/id %)) seats)))

(defn get-deal-cards
  [ai {:keys [body] :as deal-cards}]
  {:pre [(ai? ai)
         (every? ext-card? (:cards body))]}
  (let [first-seat (:game/first-seat body)]
    (every? card? (map schema/touch-card (:cards body)))
    (-> ai
        (assoc :hand (map schema/touch-card (:cards body)))
        (as-> ai
              (let [seat (find-seat (:game/seats (:game ai)) (:seat/id first-seat))]
                (assoc-in ai [:game :game/first-seat] seat))))))

(defn wait-on-joins
  [join-game-ch ai]
  {:pre [(ai? ai)]}
  (go (->> join-game-ch
           (async/take (:game/num-players ai))
           (async/into [])
           (<!)
           (map :body)
           (sort-by :seat/position))))

(defn find-suit [suit-name]
  (first (filter #(= suit-name (:card.suit/name %)) schema/suits)))

(defn touch-suit [bid]
  (if (contains? bid :bid/suit)
    (update-in bid [:bid/suit] find-suit)
    bid))

(defn get-bid-table
  [ai]
  (go
    (map touch-suit (<! (send! ai :bid-table {})))))

(defn ready-game
  [ai game-id]
  {:pre [(uuid? game-id)]}
  (let [{:keys [route-pub-ch]} ai]
    (go
      (-> ai
          (assoc :game (<! (game-info ai game-id)))
          (as-> ai
                (assoc ai :bid-table (<! (get-bid-table ai)))
                (assoc ai :game/num-players (count (:game/seats (:game ai)))))))))

(defn join-game-and-wait-for-others
  [ai]
  {:pre [(ai? ai)]}
  (let [{:keys [route-pub-ch]} ai
        join-game-ch (async/chan)
        deal-cards-ch (async/chan)]
    (async/sub route-pub-ch :join-game join-game-ch)
    (async/sub route-pub-ch :deal-cards deal-cards-ch)
    (go
      (as-> ai ai
            (assoc ai :seat (<! (join-game ai)))
            (assoc-in ai [:game :game/seats] (<! (wait-on-joins join-game-ch ai)))
            (get-deal-cards ai (<! deal-cards-ch))))))

(defn start-playing
  [ai game-id]
  (go
    (-> (ready-game ai game-id)
        <!
        (join-game-and-wait-for-others)
        <!
        (bids/start)
        <!
        (tricks/start)
        <!)))

(defn new-client-ai
  [this]
  (let [{:keys [log client-transport]} this
        {:keys [listener]} client-transport
        {:keys [mult]} listener
        receive-ch (async/chan)]
    (async/tap mult receive-ch)
    (merge this {:receive-ch receive-ch})))
