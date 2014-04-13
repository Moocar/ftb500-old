(ns me.moocar.ftb500.client.system
  (:require [com.stuartsierra.component :as component]
            [me.moocar.ftb500.client :as client]))

(defn new-system
  []
  (component/system-map
   :clients (repeatedly 4 client/new-http-client)))
