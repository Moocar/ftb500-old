(ns me.moocar.ftb500.client.engine.autoplay
  (:require [clojure.core.async :refer [go <!! <!]]
            [me.moocar.ftb500.client :as client]))

(defn play
  [clients]
  (let [leader (first clients)]
    (let [overall (<!!
                   (go
                     (try
                       (let [game-response (<! (client/create-game leader))]
                         (if (instance? Throwable game-response)
                           game-response
                           (let [game-id (:game-id game-response)
                                 join-responses (->> (rest clients)
                                                     (map #(client/join-game % game-id))
                                                     (doall))]
                             (doseq [ch join-responses]
                               (println "response" (<! ch)))
                             (println "all players joined"))))
                       (catch Throwable t
                         (.printStackTrace t)))))]
      (println "overall" overall)
      (when (instance? Throwable overall)
        (throw overall)))))
