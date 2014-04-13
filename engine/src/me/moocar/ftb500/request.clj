(ns me.moocar.ftb500.request)

(defmacro bad-args?
  [forms]
  `(when-not (empty? (keep (fn [rule#]
                             (when-not rule#
                               true))
                           ~forms))
     '~forms))

(defmacro wrap-bad-args-response
  [args & body]
  `(let [errors# (bad-args? ~args)]
     (if (not (empty? errors#))
       {:status 400
        :body {:msg errors#}}
       (do ~@body))))
