(ns me.moocar.ftb500.game
  (:require [clojure.core.async :refer [go >! <! put! chan]]
            [me.moocar.ftb500.deck :as deck]))
