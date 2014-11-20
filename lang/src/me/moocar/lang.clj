(ns me.moocar.lang)

(defn uuid []
  (java.util.UUID/randomUUID))

(defn uuid?
  [thing]
  (instance? java.util.UUID thing))
