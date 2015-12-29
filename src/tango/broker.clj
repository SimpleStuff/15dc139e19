(ns tango.broker
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

(defn message-dispatch [{:keys [topic payload sender] :as message}
                        {:keys [channel-connection-channels
                                file-handler-channels
                                event-access-channels] :as components}]
  {:pre [(some? channel-connection-channels)
         (some? file-handler-channels)
         (some? event-access-channels)]}
  (let [client-in-channel (:in-channel channel-connection-channels)]
    (log/info (str "Dispatching Topic [" topic "], Sender [" sender "]"))
    (match [topic payload]
           [:file/import _]
           (let [[import import-ch] (async/alts!!
                                     [[(:in-channel file-handler-channels)
                                       message]
                                      (async/timeout 500)])]
             (when (= nil import)
               (async/put! client-in-channel (merge message
                                              {:topic :file/import
                                               :payload :time-out}))))
           [:file/imported _]
           (let [[tx tx-ch] (async/alts!!
                             [[(:in-channel event-access-channels)
                               (merge message
                                      {:topic :event-access/transact})]
                              (async/timeout 500)])]
             (when (= nil tx)
               (async/put! client-in-channel (merge message
                                              {:topic :event-access/transact
                                               :payload :time-out}))))
           [:event-access/transaction-result _]
           (let [[tx tx-ch] (async/alts!!
                             [[client-in-channel
                               (merge message
                                      {:topic :event-manager/transaction-result
                                       :payload payload})]
                              (async/timeout 500)])]
             (when (= nil tx)
               {:topic :broker/unknown-topic :payload {:topic topic}}))
           [:event-manager/query q]
           (let [[query query-ch] (async/alts!!
                                   [[(:in-channel event-access-channels)
                                     (merge message
                                            {:topic :event-access/query})]
                              (async/timeout 500)])]
             (when (= nil query)
               (async/put! client-in-channel (merge message
                                              {:topic :event-access/query
                                               :payload :time-out}))))
           [:event-access/query-result q]
           (let [[tx tx-ch] (async/alts!!
                             [[client-in-channel
                               (merge message
                                      {:topic :event-manager/query-result
                                       :payload payload})]
                              (async/timeout 500)])]
             (when (= nil tx)
               {:topic :broker/unknown-topic :payload {:topic topic}}))
           :else
           (let [[unknown ch] (async/alts!!
                               [[client-in-channel
                                 {:sender sender :topic :broker/unknown-topic :payload {:topic topic}}]
                                (async/timeout 500)])]
             (when (= nil unknown)
               {:topic :broker/unknown-topic :payload {:topic topic}})))))

;; TODO - mapping :out-channels is not a greate idea, if we get a nil all msg procs
;; dies, fixit damn it!
(defn start-message-process [dispatch-fn components-m]
  (let [out-chans (vec (map :out-channel (vals components-m)))]
    (log/debug (str "Broker Channels : " out-chans))
    (log/info "Broker message process starting")
    (async/go-loop []
      (when-let [[message _] (async/alts! out-chans) ]
        (log/debug (str "Broker raw message : " message))
        (when message
          (log/info "Received message")
          (try
            ;; TODO - think about if this should be in a thread or not
            ;; might be better on main thread since else it can hide problems
            (async/thread (dispatch-fn message components-m))
            (catch Exception e
              (log/error e "Exception in MessageBroker router loop")))
          (recur))))))

;; TODO - need to fix som pub/sub pattern
(defrecord MessageBroker [channel-connection-channels file-handler-channels event-access-channels]
  component/Lifecycle
  (start [component]
    (log/report "Starting MessageBroker")
    (let [broker-process (start-message-process
                          message-dispatch 
                          {:channel-connection-channels channel-connection-channels
                           :file-handler-channels file-handler-channels
                           :event-access-channels event-access-channels})]
      (assoc component :broker-process broker-process)))
  (stop [component]
    (log/report "Stopping MessageBroker")
    (assoc component :broker-process nil :file-handler-channels nil :event-access-channels nil)))

(defn create-message-broker []
  (map->MessageBroker {})) 
