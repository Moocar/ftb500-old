(ns me.moocar.ftb500.client.ai.bids
  (:require [clojure.core.async :as async :refer [go <! go-loop]]
            [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.bid :as bids]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.ftb500.client.ai.transport :refer [send!]]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.schema :as schema
             :refer [player-bid? game? bid? seat? card?]]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]
            [me.moocar.ftb500.trick :as trick]
            [me.moocar.log :as log]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn suggest-bid
  "Determines the next bid to play"
  [ai]
  {:pre [(ai? ai)]}
  (-> schema/trumps-and-no-trumps
      (->> (drop-while #(not (bids/valid? (:game ai) %))))
      (conj nil) ;pass
      (rand-nth)))

(defn play-bid
  [ai]
  {:pre [(ai? ai)]}
  (let [my-bid (suggest-bid ai)]
    (log ai {:my-bid {:seat/id (:seat/id (:seat ai))
                      :bid/name (:bid/name my-bid)}})
    (send! ai :bid {:seat/id (:seat/id (:seat ai))
                    :bid/name (:bid/name my-bid)})))

(defn touch-bid
  [game player-bid]
  {:pre [(game? game)]}
  (assert (= 4 (count (:game/seats game))))
  (-> player-bid
      (update-in [:player-bid/seat] seats/find game)))

(defn kitty-game
  "If this player won the bidding, waits for kitty to be handed over
  and then selects 3 cards to swap out. Returns the new ai map"
  [{:keys [game hand seat] :as ai}
   kitty-ch]
  {:pre [(ai? ai)]}
  (go-try
   (if (seat= seat (:player-bid/seat (bids/winner game)))
     (let [kitty-cards (map schema/touch-card (:cards (:body (<? kitty-ch))))
           _ (assert (every? card? kitty-cards))
           all-shuffled (shuffle (concat kitty-cards hand))
           [new-kitty-cards hand] (split-at (count kitty-cards) all-shuffled)]
       (<? (send! ai :exchange-kitty {:cards new-kitty-cards
                                      :seat/id (:seat/id seat)}))
       (assoc ai :hand (set hand)))
     ai)))

(defn finalize-bidding
  [ai]
  {:pre [(ai? ai)]}
  (update-in ai [:game] trick/update-contract))

(defn play-if-turn
  "If the next seat to play is this player, then play a bid"
  [{:keys [game seat] :as ai}]
  {:pre [(ai? ai)]}
  (go-try
   (let [next-seat (bids/next-seat game)]
     (when (seat= next-seat seat)
       (log ai "My go")
       (let [response (<? (play-bid ai))]
         (when-not (= [:success] response)
           (throw (ex-info "Bid unsuccessfull" {:response response}))))))))

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
     (loop [ai (assoc-in ai [:game :game/bids] [])]
       (let [{:keys [game]} ai
             next-seat (bids/next-seat game)]
         (<? (play-if-turn ai))
         (when-let [bid (<? bids-ch)]
           (let [player-bid (touch-bid game (:bid (:body bid)))
                 ai (update-in ai [:game :game/bids] conj player-bid)]
             (player-bid? player-bid)
             (if (bids/finished? (:game ai))
               (<? (kitty-game (finalize-bidding ai) kitty-ch))
               (recur ai)))))))))
