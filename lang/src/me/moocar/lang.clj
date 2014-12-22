(ns me.moocar.lang)

(defn uuid
  ([string]
   (java.util.UUID/fromString string))
  ([]
   (java.util.UUID/randomUUID)))

(defn uuid?
  [thing]
  (instance? java.util.UUID thing))
