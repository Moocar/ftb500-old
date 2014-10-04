(ns me.moocar.ftb500.client.ai.bids
  (:require [clojure.core.async :as async :refer [go <!]]
            [me.moocar.log :as log]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.bid :as bids]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.ftb500.client :as client]))

(defn log [this msg]
  (log/log (:log this) msg))

(defn calc-bid
  [this game]
  (let [{:keys [bids bid-table]} game
        max-score (or (empty (bids/find-score bid-table (bids/last-non-pass-bid bids))) 0)
        available-bids (drop-while #(<= (:bid/score %) max-score) bid-table)
        normal-bids (remove #(re-find #"no-trumps|misere" (name %))
                            (map :bid/name available-bids))]
    (rand-nth (conj normal-bids {:bid/name :bid.name/pass}))))

(defn play-bid
  [this game]
  (let [my-bid-name (calc-bid this game)]
    (log this {:my-bid my-bid-name})
    (client/send! this :bid {:seat/id (:seat/id (:seat game))
                             :bid/name my-bid-name})))

(defn start
  [this game]
  (let [{:keys [route-pub-ch]} this
        {:keys [seat]} game
        position (:seat/position seat)
        bids-ch (async/chan)]
    (async/sub route-pub-ch :bid bids-ch)
    (go
      (log this "Starting bid")
      (try
        (when (game/first-player? game seat)
          (log this "First player")
          (<! (play-bid this game)))
        (catch Throwable t
          (.printStackTrace t))))
    #_(go-loop [bids (list)]
      (when-let [bid (:bid (<! bids-ch))]
        (let [new-bids (conj bids bid)]
          (if (bids-finished? client new-bids)
            new-bids
            (do (when-not (= :pass (my-last-bid client bids))
                  (if (my-bid? client new-bids)
                    (play-bid client new-bids)))
                (recur new-bids))))))))
