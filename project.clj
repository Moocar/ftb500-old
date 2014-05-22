(defproject me.moocar/ftb500 "0.1.0-SNAPSHOT"
  :plugins [[lein-sub "0.2.4"]]
  :sub ["log"
        "ftb500-protocols"
        "ftb500-client"
        "ftb500-engine"
        "ftb500-engine-client"])
