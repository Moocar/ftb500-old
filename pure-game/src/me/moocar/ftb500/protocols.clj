(ns me.moocar.ftb500.protocols)

(defprotocol ContractStyle
  "Contract Styles include NoTrumps, Misere, Trumps etc. In order to
  implement a contract, you must implement card ordering, and how to
  identify a trick winner"
  (card> [this card1 card2]
    "Should return true if card1 is of a higher value that card2")
  (follows-suit? [this suit play]
    "Returns true if the play follows suit"))
