(ns me.moocar.ftb500.game
  (:require [clojure.core.async :refer [go >! <! put! chan]]
            [me.moocar.ftb500.deck :as deck]))

(defrecord Game [first-player])

(defrecord BiddingGame [first-player])

(defrecord KittGame [contractor kitty])

(defrecord PlayingGame [trump-suit])

(defrecord Player [action-chan])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Helpers

(defn get-player-after
  "Returns the player whose turn it is after `player`"
  [players player]
  (let [players (take (* 2 (count players)) (cycle players))]
    (loop [[next-player & rest-players] players]
      (if (= (:id player) (:id next-player))
        (first rest-players)
        (recur rest-players)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Pre-game

(defn make-players
  [num-players]
  (map (fn [player-name]
         (let [next-chan (chan)
               action-chan (chan)]
           (go
             (loop []
               (println player-name (<! next-chan))
               (recur)))
           {:next-chan next-chan
            :action-chan action-chan
            :player-name player-name}))
       (take num-players (cycle ["Andrew" "Daniel" "Anthony" "Adam"]))))

(defn deal-hands
  [players hands]
  (doall
   (map
    (fn [player hand]
      (put! (:next-chan player)
            {:action :deal
             :hand hand}))
    players
    hands)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Bidding Game

(defn pass-bid?
  [bid]
  (= "PASS" (:bid bid)))

(defn valid-bid?
  [bid-history bid]
  {:pre [(:suit bid) (:rank bid)]}
  (let [last-non-pass-bid (last (remove pass-bid? bid-history))]
    (or (empty? last-non-pass-bid)
        (> (:rank bid) (:rank last-non-pass-bid))
        (and (= (:rank bid) (:rank last-non-pass-bid))
             (deck/suit> (:suit bid) (:suit last-non-pass-bid))))))

(defn final-bid?
  [bid-history players bid]
  (and (= "PASS" (:bid bid))
       (= (dec (count players))
          (->> (filter pass-bid? bid-history)
               (map players)
               (distinct)
               (count)))))

(defn get-next-player
  [bid-history players]
  (let [last-player (:player (last bid-history))
        non-passed-players (remove pass-bid? bid-history)]
    (when (> (count non-passed-players) 1)
      (get-player-after (distinct (map :player non-passed-players))
                        last-player))))

(defn bidding-game
  [players]
  {:pre [(sequential? players)]}
  (go
    (loop [bid-history []
           player (first players)]
      (if player
        (let [bid (<! (:action-chan player))]
          (if (valid-bid? bid-history bid)
            (let [bid-history (conj bid-history bid)]
              (if (final-bid? bid-history players bid)
                bid-history
                (recur bid-history
                       (get-next-player bid-history players))))
            (do (>! (:next-chan player)
                    {:action :invalid-bid})
                (recur bid-history player))))
        bid-history))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Kitty

(defn kitty-game
  [contractor kitty]
  (go
    (>! (:next-chan contractor)
        {:action :exchange-kitty
         :kitty kitty})
    (<! (:action-chan contractor))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ## Trick Game

(defn valid-play?
  [player trump-suit trick-history card-played]
  (or (empty? trick-history)
      (let [leading-card (first trick-history)]
        (and (some #(= (:suit leading-card)
                       ()))))))

(defn trick-game
  [bid-style players first-player]
  (go
    (loop [trick-history []
           player first-player]
      (>! (:next-chan player)
          {:action :play-card})
      (let [card-played (<! (:action-chan player))]
        (if (deck/valid-play? bid-style (:card (first trick-history)) (:hand player) card-played)
          (let [new-trick-history (conj trick-history {:player player :card card-played})]
            (if (= (count players) (count new-trick-history))
              trick-history
              (recur trick-history (get-player-after players player))))
          (do (>! (:next-chan player)
                  {:action :invalid-play})
              (recur trick-history player)))))))

(defn playing-game
  [bid-style players first-player]
  (go
    (loop [trick-number 1
           first-player first-player]
      (let [trick-result (<! (trick-game bid-style players first-player))
            winning-card (deck/winning-card bid-style trick-result)
            winning-player (:player (first (filter #(= winning-card (:card %))
                                                   trick-result)))]
        (when-not (= 10 trick-number)
          (recur (inc trick-number) winning-player))))))

(defn get-winning-bid
  [bid-history]
  (first (drop-while pass-bid? bid-history)))

(defn overall-game
  [players]
  (let [deck (deck/make-deck)
        {:keys [hands kitty]} (deck/partition-hands deck)]
    (go
      (deal-hands players hands)
      (let [bid-history (<! (bidding-game))
            winning-bid (get-winning-bid bid-history)
            contractor (:player winning-bid)
            game-bid-style (deck/new-bid-style (:bid winning-bid))]
        (<! (kitty-game contractor kitty))
        (<! (playing-game game-bid-style players contractor))
        (println "finished game")))))
