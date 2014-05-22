(ns me.moocar.ftb500.protocols)

(defprotocol Requester
  (send-request [this request]))

(defprotocol Subscriber
  (subscribe [this game-id ch]))

(defprotocol Transporter
  (register [this client-id request-ch response-ch]))
