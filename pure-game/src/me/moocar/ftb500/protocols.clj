(ns me.moocar.ftb500.protocols)

(defprotocol ContractStyle
  "Contract Styles include NoTrumps, Misere, Trumps etc. In order to
  implement a contract, you must implement card ordering, and how to
  identify a trick winner"
  (card> [this card1 card2]
    "Should return true if card1 is of a higher value that card2")
  (-trick-winner [this plays]
    "Given trick plays, should return the winning play, or nil if the
    trick is not finished yet"))
