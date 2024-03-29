(ns me.moocar.ftb500.client.ai.tricks
  (:require [clojure.core.async :as async :refer [go <! go-loop]]
            [me.moocar.async :refer [<? go-try]]
            [me.moocar.ftb500.client.ai.schema :refer [ai?]]
            [me.moocar.ftb500.client :refer [game-send!]]
            [me.moocar.ftb500.schema :as schema
             :refer [game? trick-game? play? card? suit? seat?]]
            [me.moocar.ftb500.seats :as seats :refer [seat=]]
            [me.moocar.ftb500.trick :as trick]))

(defn log [this msg]
  (async/put! (:log-ch this) msg))

(defn suggest
  "Suggest a card to play. Presumably based on amazing AI"
  [{:keys [game seat] :as ai}]
  {:pre [(trick-game? game)]}
  (let [{:keys [game/tricks]} game
        last-trick (last tricks)]
    (if (empty? last-trick)
      (rand-nth (vec (:seat/cards seat)))
      (let [leading-suit (trick/find-leading-suit last-trick)]
        (rand-nth (or (seats/get-follow-cards seat leading-suit)
                      (vec (:seat/cards seat))))))))

(defn touch-play
  [game play-card]
  {:pre [(trick-game? game)]}
  (-> play-card
      (update-in [:trick.play/card] schema/touch-card)
      (update-in [:trick.play/seat] seats/find game)))

(defn update-tricks
  "Updates the game's trick state with the latest play"
  [game play]
  {:pre [(game? game)
         (play? play)]}
  (let [{:keys [game/seats game/tricks]} game
        num-players (count seats)
        current-trick (last tricks)
        new-tricks (if (empty? tricks)
                     [{:trick/plays [play]}]
                     (if (= num-players (count (:trick/plays current-trick)))
                       (conj tricks {:trick/plays [play]})
                       (update-in tricks [(dec (count tricks)) :trick/plays] conj play)))]
    (assoc game :game/tricks new-tricks)))

(defn ext-card
  [card]
  {:pre [(card? card)]}
  (-> card
      (cond-> (:card/suit card)
              (update :card/suit select-keys [:card.suit/name]))
      (update :card/rank select-keys [:card.rank/name])))

(defn play-if-go
  "If it is this player's turn, suggest and play a card"
  [{:keys [game seat] :as ai}]
  {:pre [(ai? ai)]}
  (go-try
   (let [next-seat (trick/next-seat game)]
     (when (seat= next-seat seat)
       (let [card (ext-card (suggest ai))]
         (<? (game-send! ai :play-card {:trick.play/card card})))))))

(defn start
  "Waits until it is this player's turn and then plays a card, then
  listens for all other card plays until it is this player's turn
  again, and repeats"
  [ai]
  {:pre [(ai? ai)
         (trick-game? (:game ai))]}
  (let [{:keys [route-pub-ch game seat]} ai
        {:keys [game/bids]} game
        play-card-ch (async/chan)]
    (log ai "starting tricks ")
    (async/sub route-pub-ch :play-card play-card-ch)
    (go-try
     (loop [ai ai]
       (let [{:keys [game seat]} ai]
         (<? (play-if-go ai))
         (when-let [play (:body (<? play-card-ch))]
           (let [play (touch-play game play)
                 played-card (:trick.play/card play)
                 hand (if (seat= seat (:trick.play/seat play))
                        (disj (:seat/cards seat) played-card)
                        (:seat/cards seat))
                 game (update-tricks game play)
                 ai (-> ai
                        (assoc :game game)
                        (assoc-in [:seat :seat/cards] hand))]

             (if (trick/all-finished? game)
               (do (log ai "All tricks finished!")
                   ai)
               (recur ai)))))))))
