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
          (println "waiting for wait-ch")
          (<!! wait-ch)
          (println "wait ch finished")
          (assoc request :resopnse [:success]))))

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
           (println "into body")
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
            _ (println "ws lient" websocket-client)
            request {:route :moo/car :body "haha"}
            response (<!! (moo-async/request (:send-ch (:conn (:websocket-client websocket-client))) 
                                             request))]
        (is (= (:body response) request))))))

#_(deftest t-client-send-off
  (let [config (local-config)
        client-id-ch (async/chan 1)]
    (with-engines [{:keys [engine-system client-system]}
                   (new-engine-system config (echo-handler client-id-ch))
                   (new-client-system config)]
      (let [{:keys [websocket-server]} engine-system
            {:keys [websocket-client]} client-system]
        (<!! (moo-async/request (:send-ch websocket-client) 
                                {:route :moo/car :body "haha"}))
        (let [client-id (<!! client-id-ch)
              conn (websocket-server/get-client-conn websocket-server client-id)
              send-off-msg "Booya!!!"
              cli-recv-ch (async/chan)]
          (async/tap (:mult (:listener websocket-client)) cli-recv-ch)
          (moo-async/send-off! (:send-ch conn) send-off-msg)
          (let [client-received (:body (<!! cli-recv-ch))]
            (is (= send-off-msg client-received))))))))

#_(deftest t-client-request
  (let [config (local-config)
        wait-ch (async/chan 1)]
    (let [engine-system (component/start (new-engine-system config (waiting-handler wait-ch)))]
      (try
        (let [client-system (component/start (new-client-system config))]
          (try
            (let [{:keys [websocket-client]} client-system
                  request {:route :moo/car :body "haha"}
                  response-ch (moo-async/request (:send-ch websocket-client) 
                                                 request)]
              (println "sent. shutting")
              (component/stop client-system)
              (component/stop engine-system)
              (println "sent. shutting")
              (async/close! wait-ch)
              (let [response (<!! response-ch)]
                (is (= response request))))
            (catch Throwable t
              (.printStackTrace t)
              (throw t))
            (finally
              (component/stop client-system))))
        (finally
          (component/stop engine-system))))))
