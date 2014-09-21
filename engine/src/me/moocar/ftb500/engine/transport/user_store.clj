(ns me.moocar.ftb500.engine.transport.user-store)

(defprotocol UserStore
  (write [store client-id user-id])
  (delete [store client-id]))
