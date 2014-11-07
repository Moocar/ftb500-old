(ns me.moocar.ftb500.engine-test
  (:require [clojure.core.async :as async :refer [go <! <!! go-loop]]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test :as t]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client.transport.inline.system :as inline-client-system]
            [me.moocar.ftb500.client :as client]
            [me.moocar.ftb500.client.ai :as ai]
            [me.moocar.ftb500.engine.system :as engine-system]
            [me.moocar.log :as log]))

(defn dev-config
  []
  {:datomic {:uri "datomic:free://localhost:4334/ftb500"}})

(defn new-ai-client
  [engine config]
  (component/start (merge engine (inline-client-system/new-system config))))

(defn play-till-dealt [ai game-id]
  (go
    (-> (ai/ready-game ai game-id)
        <!
        (ai/join-game-and-wait-for-others)
        <!)))

(defn <!!all [started]
  (->> started
       (doall)
       (map <!!)
       (doall)))

(defn start-ais [clients]
  (<!!all (map ai/start clients)))

(defn start-global-timeout [engine clients]
  (go (<! (async/timeout 2000))
      (log/log (:log engine) "!!!!!!!!! Timeout and shutdown !!!!!!!!!!")
      (doseq [client clients]
        (component/stop client))
      (component/stop engine)))

(defn actually-play [clients game-id]
  (<!!all (map #(play-till-dealt % game-id) clients)))

(defn start-playing [clients]
  (go
    (let [response (<! (client/send! (first clients) :add-game {:num-players 4}))]
      (let [game-id (:game/id (second response))]
        (actually-play clients game-id)))))

(defn play
  []
  (let [config (dev-config)
        engine (component/start (engine-system/new-system config))
        clients (repeatedly 4 #(ai/new-client-ai (new-ai-client engine config)))
        log (:log engine)]
    (try
      (let [clients (start-ais clients)]
        (start-global-timeout engine clients)
        (<!! (start-playing clients)))
      (catch Throwable t
        (log/log (:log engine) t))
      (finally
        (doseq [client clients]
          (component/stop client))
        (component/stop engine)))))

(t/deftest cards-should-be-unique
  (let [clients (play)
        all-cards (flatten (map :hand clients))]
    (t/is (= (count (set all-cards))
             40))))

