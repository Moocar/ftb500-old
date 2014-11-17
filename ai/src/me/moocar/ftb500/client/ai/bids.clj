(ns me.moocar.ftb500.client.ai.bids
  (:require [clojure.core.async :as async :refer [go <! go-loop]]
            [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.bid :as bids]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.schema :as schema
             :refer [player-bid? game? bid? seat? card?]]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]
            [me.moocar.ftb500.trick :as trick]
            [me.moocar.log :as log]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn calc-bid
  [ai game]
  {:pre [(game? game)]}
  (-> schema/trumps-and-no-trumps
      (->> (drop-while #(not (bids/valid? game %))))
      (conj nil) ;pass
      (rand-nth)))

(defn play-bid
  [ai game]
  {:pre [(ai? ai)
         (game? game)]}
  (let [my-bid (calc-bid ai game)]
    (log ai {:my-bid {:seat/id (:seat/id (:seat ai))
                      :bid/name (:bid/name my-bid)}})
    (client/send! ai :bid {:seat/id (:seat/id (:seat ai))
                           :bid/name (:bid/name my-bid)})))

(defn touch-bid
  [game player-bid]
  {:pre [(game? game)]}
  (assert (= 4 (count (:game/seats game))))
  (-> player-bid
      (update-in [:player-bid/seat] seats/find game)))

(defn kitty-game
  [ai kitty-ch]
  {:pre [(ai? ai)]}
  (let [{:keys [game hand]} ai]
    (go-try
      (log ai {:msg "In kitty game now"})
      (if (seat= (:seat ai)
                 (:player-bid/seat (bids/winner game)))
        (do 
          (log ai "Waiting for kitty")
          (let [kitty-cards (map schema/touch-card (:cards (:body (<? kitty-ch))))
                _ (assert (every? card? kitty-cards))
                all-shuffled (shuffle (concat kitty-cards hand))
                [new-kitty-cards hand] (split-at 3 all-shuffled)
                response (<? (client/send! ai :exchange-kitty {:cards new-kitty-cards
                                                               :seat/id (:seat/id (:seat ai))}))]
            (if-not (keyword? response)
              (assoc ai :hand (set hand))
              (throw (ex-info "Failed to exchange kitty"
                              {:reason response})))))
        ai))))

(defn new-kitty-game
  [ai game kitty-ch]
  (let [contract (trick/new-contract game (bids/winner game))]
    (go-try
      (-> ai
          (assoc :game (assoc game :contract-style contract))
          (kitty-game kitty-ch)
          <?))))

(defn start
  [ai]
  {:pre [(ai? ai)]}
  (let [{:keys [route-pub-ch seat game]} ai
        position (:seat/position seat)
        bids-ch (async/chan)
        kitty-ch (async/chan)]
    (log ai  (str "(" position ") " "starting bidding "))
    (async/sub route-pub-ch :bid bids-ch)
    (async/sub route-pub-ch :kitty kitty-ch)

    (go-try
     (loop [game (assoc game :game/bids [])]
       (let [next-seat (bids/next-seat game)]
         (when (seat= next-seat seat)
           (log ai "My go")
           (let [response (<? (play-bid ai game))]
             (when-not (= [:success] response)
               (throw (ex-info "Bid unsuccessfull" {:response response}))))))
       (when-let [bid (<? bids-ch)]
         (let [player-bid (touch-bid game (:bid (:body bid)))
               game (update-in game [:game/bids] conj player-bid)]
           (player-bid? player-bid)
           (if (bids/finished? game)
             (<? (new-kitty-game ai game kitty-ch))
             (recur game))))))))
