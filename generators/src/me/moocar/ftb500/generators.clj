(ns me.moocar.ftb500.generators
  (:require [clojure.test.check.generators :as gen]))

(def char-hex
  (gen/fmap char
            (gen/one-of [(gen/choose 48 57)
                         (gen/choose 65 70)
                         (gen/choose 97 102)])))

(defn sized-hex-string
  [size]
  (gen/fmap clojure.string/join
            (apply gen/tuple (repeat size char-hex))))

(def uuid
  (gen/fmap #(clojure.string/join "-" %)
            (gen/tuple (sized-hex-string 8)
                       (sized-hex-string 4)
                       (sized-hex-string 4)
                       (sized-hex-string 4)
                       (sized-hex-string 12))))

(def player
  (gen/fmap (fn [[uuid]]
              {:player/id uuid})
            (gen/tuple uuid)))

(def num-players
  (gen/choose 0 3))

(def seat
  (gen/fmap (fn [vals]
              (zipmap [:seat/id :seat/player :seat/position] vals))
            (gen/tuple uuid
                       player
                       num-players)))

(def deck
  (gen/fmap (fn [vals]
              (zipmap [:deck/num-players] vals))
            (gen/tuple num-players)))

(def bids
  [{:bid/name :bid.name/six-spades
    :bid/score 40}
   {:bid/name :bid.name/six-clubs
    :bid/score 60}
   {:bid/name :bid.name/six-diamonds
    :bid/score 80}
   {:bid/name :bid.name/six-hearts
    :bid/score 100}
   {:bid/name :bid.name/six-no-trumps
    :bid/score 120}
   {:bid/name :bid.name/seven-spades
    :bid/score 140}
   {:bid/name :bid.name/seven-clubs
    :bid/score 160}
   {:bid/name :bid.name/seven-diamonds
    :bid/score 180}
   {:bid/name :bid.name/seven-hearts
    :bid/score 200}
   {:bid/name :bid.name/seven-no-trumps
    :bid/score 220}
   {:bid/name :bid.name/eight-spades
    :bid/score 240}
   {:bid/name :bid.name/eight-clubs
    :bid/score 260}
   {:bid/name :bid.name/eight-diamonds
    :bid/score 280}
   {:bid/name :bid.name/eight-hearts
    :bid/score 300}
   {:bid/name :bid.name/eight-no-trumps
    :bid/score 320}
   {:bid/name :bid.name/nine-spades
    :bid/score 340}
   {:bid/name :bid.name/nine-clubs
    :bid/score 360}
   {:bid/name :bid.name/nine-diamonds
    :bid/score 380}
   {:bid/name :bid.name/nine-hearts
    :bid/score 400}
   {:bid/name :bid.name/nine-no-trumps
    :bid/score 420}
   {:bid/name :bid.name/ten-spades
    :bid/score 440}
   {:bid/name :bid.name/ten-clubs
    :bid/score 460}
   {:bid/name :bid.name/ten-diamonds
    :bid/score 480}
   {:bid/name :bid.name/ten-hearts
    :bid/score 500}
   {:bid/name :bid.name/ten-no-trumps
    :bid/score 500}
   {:bid/name :bid.name/misere
    :bid/score 250}
   {:bid/name :bid.name/open-misere
    :bid/score 500}])

(def bid-names
  (gen/fmap :bid/name (gen/elements bids)))

(def bid-name
  (gen/elements bid-names))

(def bid
  (gen/elements bids))

(def player-bid
  (gen/fmap #(zipmap [:seat :bid] %)
            (gen/bind gen/boolean
                      (fn [v]
                        (if v
                          (gen/tuple seat bid)
                          (gen/tuple seat))))))

