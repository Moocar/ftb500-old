(ns me.moocar.ftb500.client.sh
  (:require [com.stuartsierra.component :as component]
            [clojure.pprint :refer [print-table]]
            [clojure.core.async :as async :refer [thread <! go]]
            [me.moocar.async :refer [<? go-try <!!?]]
            [me.moocar.ftb500.client :as client :refer [game-send! send!]]
            [me.moocar.ftb500.game :as game]
            [me.moocar.ftb500.schema :as schema
             :refer [game? seat? bid? player? uuid? ext-card? card?]]
            [me.moocar.ftb500.seats :as seats]
            [me.moocar.lang :refer [uuid]]))

(defn empty-array []
  (into-array Object []))

(defn prompt-name [{:keys [console]}]
  #_(let [name (.readLine console "Please enter your name: " (empty-array))]
    (.format console "Cheers %s\n" (into-array [name]))
    (.flush console)
    name)
  "Moocar")

(defn prompt-game-id [{:keys [console writer]}]
  (loop []
    (let [string (.readLine console "Which game would you like to join? " (empty-array))]
      (or (try
            (uuid string)
            (catch Throwable t nil))
          (do
            (binding [*out* writer]
              (println "\nYou fucked up. game-id should be a full edn uuid (copy paste you fool!)"))
            (recur))))))

(defn prompt-seat-position [{:keys [console writer]}]
  (loop []
    (let [string (.readLine console "Which seat position? " (empty-array))]
      (or (try
            (Integer/parseInt string)
            (catch Throwable t nil))
          (do
            (binding [*out* writer]
              (println "\nYou fucked up. seat-id should be a seat position number"))
            (recur))))))

(defn prompt-seat
  [{:keys [console writer game] :as client}]
  (loop []
    (let [position (prompt-seat-position client)]
      (or (seats/find-by-position position game)
          (do
            (binding [*out* writer]
              (println "You fucked up. position not found in game"))
            (recur))))))

(defn prompt-seat-loop
  [{:keys [console writer] :as client}]
  (go-try
   (loop []
     (let [seat (prompt-seat client)]
       (println "seat" seat)
       (let [assigned-seat (<! (client/join-game client seat))]
         (if (= [:seat-taken] (:error (ex-data assigned-seat)))
           (do
             (binding [*out* writer]
               (println "You fucked up. seat is already taken"))
             (recur))
           (if (ex-data assigned-seat)
             (throw assigned-seat)
             assigned-seat)))))))

(def game
  {:game/bids [],
   :game/seats
   [{:seat/player
     {:user/id #uuid "3c399a63-0ae9-4e82-aca9-f43efd595fbb"},
     :seat/position 0,
     :seat/id #uuid "549a272e-ebde-4b98-b89c-b985d4ca7e69"}
    {:seat/player
     {:user/id #uuid "c187c135-927f-42ec-907e-56f3f2bb0d2c"},
     :seat/position 1,
     :seat/id #uuid "549a272e-2ff5-4838-a5e5-67cb53b953f1"}
    {:seat/player
     {:user/id #uuid "215e6ed3-b149-4b93-be07-542855302492"},
     :seat/position 2,
     :seat/id #uuid "549a272e-a3b6-49b1-addc-26915d4d6922"}
    {:seat/position 3,
     :seat/id #uuid "549a272e-2b80-418b-b0ed-ccf48f017340"}],})

(defn format-seat [seat]
  (-> seat
      (update :seat/player (comp #(when % (subs (str %) 20)) :user/id))
      (select-keys [:seat/position :seat/player])))

(defn print-game
  [writer game]
  (binding [*out* writer]
    (print "Seats:")
    (->> game
         :game/seats
         (map format-seat)
         print-table)))

(defn pr-join-game [{:keys [writer]}]
  (fn [body]
    (binding [*out* writer]
      (println "player joined" body))))

(defrecord ShClient [console transport log-ch]
  component/Lifecycle
  (start [this]
    (assoc
     this
     :ch
     (thread
       (let [writer (.writer console)
             {:keys [listener]} transport
             {:keys [mult]} listener
             tapped-log-ch (async/chan)
             _ (async/tap mult tapped-log-ch)
             log-pub (async/pub tapped-log-ch :route)
             player-name (prompt-name this)
             game-id (prompt-game-id this)
             user-id (uuid)
             this (assoc this
                         :writer writer
                         :route-pub-ch log-pub
                         :player {:player/name "Anthony"
                                  :user/id user-id})]
         (async/sub log-pub :create-game (async/chan 1 (keep (constantly nil))))
         (async/sub log-pub :join-game (async/chan 1 (keep (pr-join-game this))))
         (println "game id" game-id)
         (<!!?
          (go-try
           (and (<? (send! this :signup {:user-id user-id}))
                (<? (send! this :login {:user-id user-id})))
           (-> (client/ready-game this game-id)
               <?
               (as-> this
                   (let [{:keys [game]} this]
                     (print-game writer game)
                     (if (game/full? game)
                       (go
                         (binding [*out* writer]
                           (println "Game is already full. Bad luck")))
                       (client/join-game-and-wait-for-others
                        this
                        (prompt-seat-loop this)))))
               <?)))))))
  (stop [this]
    this))

(defn new-sh-client [console config]
  (component/using (map->ShClient {:console console})
    [:transport :log-ch]))
