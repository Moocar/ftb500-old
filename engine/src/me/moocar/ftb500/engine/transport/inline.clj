(ns me.moocar.ftb500.engine.transport.inline
  (:require [clojure.core.async :as async :refer [put!]]
            [com.stuartsierra.component :as component]
            [me.moocar.ftb500.engine.transport :as engine-transport]))

(defrecord EngineInlineTransport [client-receive-chs]
  engine-transport/EngineTransport
  (-send! [this user-id msg]
    (let [client-receive-ch (get (deref client-receive-chs) user-id)]
      (put! client-receive-ch msg))))

(defn new-engine-inline-transport []
  (map->EngineInlineTransport {:client-receive-chs (async/chan)}))
