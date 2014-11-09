(ns me.moocar.ftb500.client.ai.bids
  (:require [clojure.core.async :as async :refer [go <! go-loop]]
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

(defn find-normal-bids
  "Returns bids that are neither no trumps or misere"
  [bids]
  {:pre [(every? bid? bids)]}
  (filter (comp #{:bid.contract-style/trumps :bid.contract-style/no-trumps}
                :bid/contract-style)
          bids))

(defn calc-bid
  [ai game player-bids]
  {:pre [(game? game)
         (every? player-bid? player-bids)]}
  (let [{:keys [bid-table]} ai
        max-score (or (when-let [last-non-pass (bids/last-non-pass-bid player-bids)]
                        (bids/find-score bid-table last-non-pass))
                      0)]
    (-> bid-table
        (->> (drop-while #(<= (:bid/score %) max-score)))
        (find-normal-bids)
        (conj nil) ;pass
        (rand-nth))))

(defn play-bid
  [ai game player-bids]
  {:pre [(ai? ai)
         (game? game)
         (every? player-bid? player-bids)]}
  (let [my-bid (calc-bid ai game player-bids)]
    (log ai {:my-bid {:seat/id (:seat/id (:seat ai))
                      :bid/name (:bid/name my-bid)}})
    (client/send! ai :bid {:seat/id (:seat/id (:seat ai))
                           :bid/name (:bid/name my-bid)})))

(defn touch-bid
  [game player-bid]
  {:pre [(game? game)]}
  (assert (= 4 (count (:game/seats game))))
  (let [seat (first (filter #(= (:seat/id %)
                                (:seat/id (:player-bid/seat player-bid)))
                            (:game/seats game)))]
    (seat? seat)
    (assoc player-bid
      :player-bid/seat seat)))

(defn kitty-game
  [ai kitty-ch]
  {:pre [(ai? ai)]}
  (let [{:keys [game hand]} ai
        bids (:game/bids game)]
    (go
      (log ai {:msg "In kitty game now"})
      (if (seat= (:seat ai)
                 (:player-bid/seat (bids/winning-bid bids)))
        (do (log ai "Waiting for kitty")
            (let [kitty-cards (map schema/touch-card (:cards (:body (<! kitty-ch))))]
              (assert (every? card? kitty-cards))
              (let [all-shuffled (shuffle (concat kitty-cards hand))
                    [new-kitty-cards hand] (split-at 3 all-shuffled)]
                (let [response (<! (client/send! ai :exchange-kitty {:cards new-kitty-cards
                                                                     :seat/id (:seat/id (:seat ai))}))]
                 (if-not (keyword? response)
                   (assoc ai :hand (set hand))
                   (throw (ex-info "Failed to exchange kitty"
                                   {:reason response})))))))
        ai))))

(defn start
  [ai]
  {:pre [(ai? ai)]}
  (let [{:keys [route-pub-ch seat game]} ai
        {:keys [game/seats]} game
        position (:seat/position seat)
        bids-ch (async/chan)
        kitty-ch (async/chan)]
    (log ai (str "starting bidding " (:hand ai)))
    (async/sub route-pub-ch :bid bids-ch)
    (async/sub route-pub-ch :kitty kitty-ch)
    (go
      (try
        (when (game/first-player? game seat)
          (log ai "first player playing")
          (<! (play-bid ai game nil)))
        (catch Throwable t
          (.printStackTrace t))))
    (go-loop [bids (list)]
      (when-let [bid (<! bids-ch)]
        (let [player-bid (touch-bid game (:bid (:body bid)))
              new-bids (conj bids player-bid)]
          (player-bid? player-bid)
          (if (bids/finished? game new-bids)
            (let [new-game (assoc game
                             :game/bids new-bids)
                  contract (trick/new-contract new-game (bids/winning-bid new-bids))]
              (-> ai
                  (assoc :game (assoc new-game :contract-style contract))
                  (kitty-game kitty-ch)
                  <!))
            (do
              (when (bids/your-go? game seats new-bids seat)
                (log ai "My go")
                (let [response (<! (play-bid ai game new-bids))]
                  (when-not (= [:success] response)
                    (throw (ex-info "Bid unsuccessfull" {:response response})))))
              (recur new-bids))))))))
