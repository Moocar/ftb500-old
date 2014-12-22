(ns me.moocar.ftb500.websocket-server-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async :refer [<!!]]
            [com.stuartsierra.component :as component]
            [me.moocar.async :as moo-async]
            [me.moocar.ftb500.engine.transport.websocket :as websocket-server]
            [me.moocar.ftb500.client.transport.websocket :as websocket-client]
            [me.moocar.log :as log]))

(defn echo-handler
  [client-id-ch]
  (keep (fn [{:keys [body] :as request}]
          #_(async/put! client-id-ch (:client/id (:conn request)))
          (assoc request :response body))))

(defn waiting-handler
  [wait-ch]
  (keep (fn [{:keys [response-cb body] :as request}]
          (<!! wait-ch)
          (assoc request :response [:success]))))

(defn new-engine-system
  [config handler]
  (component/system-map
   :websocket-server (websocket-server/new-websocket-server config)
   :engine-handler-xf handler))

(defn new-client-system
  [config]
  (component/system-map
   :websocket-client (websocket-client/new-websocket-client config)
   :log (log/new-logger config)))

(defn local-config []
  {:engine {:websocket {:server {:port 8083
                                 :hostname "localhost"}}}})

(defmacro with-engines 
  [[binding-form engine-system-map client-system-map] & body]
  `(let [engine-system# (component/start ~engine-system-map)]
     (try
       (let [client-system# (component/start ~client-system-map)
             ~binding-form {:engine-system engine-system#
                            :client-system client-system#}]
         (try
           ~@body
           (catch Throwable t#
             (.printStackTrace t#)
             (throw t#))
           (finally
             (component/stop client-system#))))
       (finally
         (component/stop engine-system#)))))


(deftest t-client-request
  (let [config (local-config)]
    (with-engines [{:keys [engine-system client-system]}
                   (new-engine-system config (echo-handler (async/chan 1)))
                   (new-client-system config)]
      (let [{:keys [websocket-client]} client-system
            request {:route :moo/car :body "haha"}
            response (<!! (moo-async/request (:send-ch (:conn websocket-client)) 
                                             request))]
        (is (= (:body response) request))))))


