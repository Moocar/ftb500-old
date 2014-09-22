(ns me.moocar.ftb500.client.ai.bids
  (:require [clojure.core.async :as async]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.bid :as bids]))

#_(defn calc-bid
  [this bids]
  (let [max-score (or (bids/last-non-pass-bid bids) 0)
        available-bids (drop-while #(<= (:score %) max-score) bid-table)]
    (rand-nth (conj (remove #(re-find #"no-trumps|misere" (name %)) (map :name available-bids)) :pass))))

#_(defn play-bid
  [this bids]
  (let [my-bid (calc-bid client bids)]
    (log client {:my-bid my-bido})
    (client/bid client my-bid)))

(defn start
  [this game]
  #_(let [{:keys [route-pub-ch]} this
        {:keys [seat]} game
        position (:seat/position seat)
        bids-ch (async/chan)]
    (async/sub route-pub-ch :bid bids-ch)
    (go
      (try
        (when (game/first-player? game seat)
          (play-bid client []))
        (catch Throwable t
          (.printStackTrace t))))
    (go-loop [bids (list)]
      (when-let [bid (:bid (<! bids-ch))]
        (let [new-bids (conj bids bid)]
          (if (bids-finished? client new-bids)
            new-bids
            (do (when-not (= :pass (my-last-bid client bids))
                  (if (my-bid? client new-bids)
                    (play-bid client new-bids)))
                (recur new-bids))))))))
