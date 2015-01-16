(ns me.moocar.ftb500.client.sh.main
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :refer [<!!]]
            [me.moocar.async :refer [<!!?]]
            [me.moocar.ftb500.client.sh.system :as sh-system])
  (:gen-class))

(defn run [console config]
  (while true
    (try
      (let [system (component/start (sh-system/new-system console config))]
        (<!!? (:ch (:sh-client system)))
        (component/stop system))
      (catch Throwable t
        (.printStackTrace t)))))

(defn -main [& args]
  (try
    (let [config (read-string (slurp "local_config.edn"))
          console (System/console)]
      (assert console "No Console found. If running from lein use `lein trampoline run`")
      (run console config)
      (System/exit 0))
    (catch Throwable t
      (.printStackTrace t)
      (System/exit 70))))
