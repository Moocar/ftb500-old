(ns me.moocar.ftb500.seats
  (:require [me.moocar.ftb500.card :refer [card=]]
            [me.moocar.ftb500.schema
             :refer [seat? player? game? uuid? suit? card?]])
  (:refer-clojure :exclude [next find]))

(defn seat=
  [seat1 seat2]
  (when (and seat1 seat2)
    (assert (seat? seat1))
    (assert (seat? seat2))
    (apply = (map :seat/id [seat1 seat2]))))

(defn find
  "Find the full seat for the partial seat ({:seat/id ...})"
  [partial-seat game]
  {:pre [(game? game)
         (uuid? (:seat/id partial-seat))]}
  (first (filter #(= (:seat/id %)
                     (:seat/id partial-seat))
                 (:game/seats game))))

(defn player=
  [player1 player2]
  {:pre [(player? player1)
         (player? player2)]}
  (apply = (map :user/id [player1 player2])))

(defn taken?
  "Returns true if the seat is already taken"
  [seat]
  {:pre [(seat? seat)]}
  (contains? seat :seat/player))

(defn find-assigned
  "Returns the seat that is assigned to player"
  [game player]
  {:pre [(game? game)
         (player? player)]}
  (first (filter #(= (:user/id player) (:user/id (:seat/player %)))
                 (:game/seats game))))

(defn find-available
  "Returns the first seat that has not been taken yet"
  [game]
  {:pre [(game? game)]}
  (first (remove taken? (:game/seats game))))

(defn next
  [seats seat]
  {:pre [(every? seat? seats)
         (not-empty seats)
         (seat? seat)]}
  (let [next-seat-position (mod (inc (:seat/position seat))
                                (count seats))]
    (first (filter #(= next-seat-position (:seat/position %))
                   seats))))

(defn in-team?
  [team seat]
  (boolean
   (some #(seat= % seat) team)))

(defn teams
  "Returns a set of teams, where a team is a set of seats"
  [{:keys [game/seats] :as game}]
  {:pre [(game? game)]}
  (let [seats (sort-by :seat/position seats)]
    #{(set (take-nth 2 seats))
      (set (take-nth 2 (rest seats)))}))

(defn get-follow-cards
  "Returns the cards from the seat's hand that follow suit"
  [seat suit]
  {:pre [(seat? seat)
         (suit? suit)]}
  (seq (filter #(= suit (:card/suit %))
               (:seat/cards seat))))

(defn has-card?
  "Returns true if seat has the card in their hand"
  [seat card]
  {:pre [(seat? seat)
         (card? card)]}
  (some #(card= card %)
        (:seat/cards seat)))
