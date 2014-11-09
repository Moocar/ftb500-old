(ns me.moocar.ftb500.client.ai.tricks
  (:require [clojure.core.async :as async :refer [go <! go-loop]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.bid :as bid]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.schema :as schema
             :refer [player-bid? game? bid? seat? card? trick-game?]]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.ftb500.trick :as trick]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn calc-next-card
  [ai]
  {:pre [(trick-game? (:game ai))]}
  (let [{:keys [game hand]} ai
        {:keys [game/tricks]} game
        last-trick (last tricks)]
    (if (empty? last-trick)
      (rand-nth (vec hand))
      (let [leading-suit (trick/find-leading-suit (last-trick))]
        (or (when-let [suit-cards (seq (filter #(= leading-suit (:card/suit %)) hand))]
              (rand-nth suit-cards))
            (rand-nth (vec hand)))))))

(defn play-card [ai]
  {:pre [(ai? ai)]}
  (go
    (let [card (calc-next-card ai)]
      (log ai (str "Sending card: " card))
      (let [result (<! (client/send! ai :play-card {:seat/id (:seat/id (:seat ai))
                                                    :trick.play/card card}))]
        (log ai (str "Result: " result))
        (if (keyword? result)
          (log ai (str "Play card failure: " result)))))))

(defn won-bidding? [ai]
  (seat= (:seat ai)
         (:player-bid/seat (bid/winning-bid (:game/bids (:game ai))))))

(defn start
  [ai]
  {:pre [(ai? ai)
         (trick-game? (:game ai))]}
  (let [{:keys [route-pub-ch game]} ai
        bids (:game/bids game)
        trick-ch (async/chan)]
    (log ai "starting tricks ")
    (async/sub route-pub-ch :trick trick-ch)
    (go
      (try
        (when (won-bidding? ai)
          (<! (play-card ai)))
        (catch Throwable t
          (.printStackTrace t))))
    #_(go-loop [bids (list)]
      (when-let [bid (<! bids-ch)]
        (let [player-bid (touch-bid game (:bid (:body bid)))
              new-bids (conj bids player-bid)]
          (player-bid? player-bid)
          (if (bids/finished? game new-bids)
            (kitty-game (assoc-in ai [:game :game/bids] new-bids) kitty-ch)
            (do
              (when (bids/your-go? game seats new-bids seat)
                (log ai "My go")
                (let [response (<! (play-bid ai game new-bids))]
                  (when-not (= [:success] response)
                    (throw (ex-info "Bid unsuccessfull" {:response response})))))
              (recur new-bids))))))))
