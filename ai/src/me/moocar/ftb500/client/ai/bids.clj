(ns me.moocar.ftb500.client.ai.bids
  (:require [clojure.core.async :as async :refer [go <! go-loop]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.bid :as bids]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.schema :refer [player-bid? game? bid? seat?]]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.ftb500.seats :as seats]))

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
    (log ai {:my-bid my-bid})
    (client/send! ai :bid {:seat/id (:seat/id (:seat ai))
                           :bid/name (:bid/name my-bid)})))

(defn touch-bid
  [game player-bid]
  {:pre [(game? game)]}
  (let [seat (first (filter #(= (:seat/id %)
                                (:seat/id (:player-bid/seat player-bid)))
                            (:game/seats game)))]
    (seat? seat)
    {:player-bid/bid (:player-bid/bid player-bid)
     :player-bid/seat seat}))

(defn start
  [ai]
  {:pre [(ai? ai)]}
  (let [{:keys [route-pub-ch seat game]} ai
        seats (:game/seats game)
        position (:seat/position seat)
        bids-ch (async/chan)]
    (log ai (str "starting bidding " (:hand ai)))
    (async/sub route-pub-ch :bid bids-ch)
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
            new-bids
            (do
              (when (bids/your-go? game seats new-bids seat)
                (log ai "My go")
                (let [response (<! (play-bid ai game new-bids))]
                  (log ai {:response response})
                  (when-not (= [:success] response)
                    (throw (ex-info "Bid unsuccessfull" {:response response})))))
              (recur new-bids))))))))
