(ns me.moocar.ftb500.client.ai.bids
  (:require [clojure.core.async :as async :refer [go <! go-loop]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.bid :as bids]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.client :as client]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn calc-bid
  [this game bids]
  (let [{:keys [bid-table]} game
        max-score (or  (bids/find-score bid-table (bids/last-non-pass-bid bids)) 0)
        available-bids (drop-while #(<= (:bid/score %) max-score) bid-table)
        normal-bids (remove #(re-find #"no-trumps|misere" (name %))
                            (map :bid/name available-bids))]
    (rand-nth (conj normal-bids :bid.name/pass))))

(defn play-bid
  [this game bids]
  (let [my-bid-name (calc-bid this game bids)]
    (log this {:my-bid my-bid-name})
    (client/send! this :bid {:seat/id (:seat/id (:seat game))
                             :bid/name my-bid-name})))

(defn touch-bid
  [game bid]
  (let [seat (first (filter #(= (:seat/id %)
                                (:seat/id (:seat bid)))
                            (:game/seats game)))]
    (assert seat)
    {:bid (:bid bid)
     :seat seat}))

(defn start
  [this game]
  (let [{:keys [route-pub-ch]} this
        {:keys [seat]} game
        seats (:game/seats game)
        position (:seat/position seat)
        bids-ch (async/chan)]
    (assert (number? position))
    (async/sub route-pub-ch :bid bids-ch)
    (go
      (try
        (when (game/first-player? game seat)
          (<! (play-bid this game nil)))
        (catch Throwable t
          (.printStackTrace t))))
    (go-loop [bids (list)]
      (when-let [bid (<! bids-ch)]
        (let [bid (touch-bid game (:bid (:body bid)))
              new-bids (conj bids bid)]
          (if (bids/finished? game new-bids)
            new-bids
            (do
              (when (bids/your-go? game seats new-bids seat)
                (log this "My go")
                (let [response (<! (play-bid this game new-bids))]
                  (log this {:response response})
                  (when-not (= [:success] response)
                    (throw (ex-info "Bid unsuccessfull" {:response response})))))
              (recur new-bids))))))))
