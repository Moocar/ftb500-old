(ns me.moocar.ftb500.game)

(defn full?
  [game]
  (= (:game/num-players game)
     (count (:game/seats game))))

(defn my-seat?
  [seat player]
  (and (:seat/player seat)
       (= (:player/id player)
          (:seat/player seat))))

(defn seat-taken?
  [seat player]
  (and (:seat/player seat)
       (not= (:player/id player)
             (:seat/player seat))))

(defn already-dealt?
  [game]
  (contains? game :game.kitty/cards))
