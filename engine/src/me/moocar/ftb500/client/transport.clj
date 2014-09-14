(ns me.moocar.ftb500.client.transport)

(defprotocol ClientTransport
  (-send! [this msg] [this msg timeout-ms callback]))

(defn send!
  ([this msg]
     (-send! this msg))
  ([this msg timeout-ms callback]
     (-send! this msg timeout-ms callback)))
