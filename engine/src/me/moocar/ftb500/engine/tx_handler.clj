(ns me.moocar.ftb500.engine.tx-handler)

(defprotocol TxHandler
  (handle [this user-ids tx]))
