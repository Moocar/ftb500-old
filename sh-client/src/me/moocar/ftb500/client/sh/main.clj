(ns me.moocar.ftb500.client.sh.main
  (:require [com.stuartsierra.component :as component] 
            [me.moocar.ftb500.client.sh.system :as sh-system])
  (:gen-class))

(defn run [config]
  (component/start (sh-system/new-system config)))

(defn -main [& args]
  (try
    (let [config (read-string (slurp "local_config.edn"))]
      (run config)
      (System/exit 0))
    (catch Throwable t
      (.printStackTrace t)
      (System/exit 70))))
