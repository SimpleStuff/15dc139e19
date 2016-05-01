(ns tango.broker
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [tango.datomic-storage :as d]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; This should be componitized

(defn select-activity [conn activity]
  (do
    (d/select-round conn activity)
    (log/info (str "Application selected round changed : " ))))

(defn selected-activity [conn]
  (log/info (str "Application selected round : " (d/get-selected-activity conn '[:activity/name]))))

(defn fix-lookup-refs [result]
  (merge
    result
    {:result/participant {:participant/id (:result/participant result)}
     :result/activity {:activity/id (:result/activity result)}
     :result/adjudicator {:adjudicator/id (:result/adjudicator result)}}))

(defn set-result [conn result]
  (do
    (log/info (str "Set Result : " ) result)

    (d/set-results conn [(merge
                           result
                           {:result/participant {:participant/id (:result/participant result)}
                            :result/activity {:activity/id (:result/activity result)}
                            :result/adjudicator {:adjudicator/id (:result/adjudicator result)}})])))

(defn confirm-results [conn results adjudicator activity]
  (do
    (log/info (str "Confirm Results " results " - " adjudicator " - " activity))
    (log/debug (str "Results Count " (count (d/query-all-results conn ['*]))))
    (let [tx
          (d/set-results conn results)
          id (:activity/id activity)
          adj-id (:adjudicator/id adjudicator)
          conf {:activity/id id
                :activity/confirmed-by adjudicator}
          ]
      (log/info id)
      (log/info adj-id)
      (log/info conf)
      (d/confirm-activity conn [conf])
      ;(log/info "Transaction " tx-2)
      (log/debug (str "Results After Count " (count (d/query-all-results conn ['*])))))
    ))

(defn start-result-rules-engine [in-ch out-ch client-in-channel datomic-storage-uri]
  (async/go-loop []
    (when-let [message (async/<! in-ch)]
      (when message
        (try
          (let [topic (:topic message)
                payload (:payload message)]
            (log/trace (str "Result Rules received: " message))
            (log/info (str "Result Received Topic: [" topic "]"))
            (log/info (str "Result Received payload: [" payload "]"))
            (match [topic payload]
                   ['app/select-activity _]
                   (do
                     (log/info (str "Select activity " payload))
                     ;; TODO - should the connection be kept open?
                     (select-activity (d/create-connection datomic-storage-uri) payload))
                   ['participant/set-result _]
                   (do
                     (log/info (str "Set Result"))
                     (set-result (d/create-connection datomic-storage-uri) payload))
                   ['app/confirm-marks _]
                   (do
                     (log/info (str "Confirm Result " payload))
                     (confirm-results (d/create-connection datomic-storage-uri)
                                      (into [] (:results payload))
                                      (:adjudicator payload)
                                      (:activity payload)))
                   ;[:app/selected-activity _]
                   ;(do
                   ;  (log/info (str "Selected activity " (:app/selected-activity payload)))
                   ;  (selected-activity (d/create-connection datomic-storage-uri)))
                   [:set-result p]
                   (log/info "Mark X")
                   :else (async/>!!
                           out-ch
                           {:topic :rules/unkown-topic :payload {:topic topic}})

                   )
            (let [[tx tx-ch] (async/alts!!
                               [[client-in-channel
                                 ;(merge message)
                                 {:topic   :tx/accepted
                                  :payload (if (= topic 'app/confirm-marks)
                                             {:topic topic :payload (:adjudicator payload)}
                                             topic)}]
                                (async/timeout 500)])]))
          (catch Exception e
            (log/error e "Exception in Broker message go loop")
            (async/>! out-ch (str "Exception message: " (.getMessage e)))))
        (recur)))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;


(defn message-dispatch [{:keys [topic payload sender] :as message}
                        {:keys [channel-connection-channels
                                file-handler-channels
                                event-access-channels
                                rules-engine-channels] :as components}]
  {:pre [(some? channel-connection-channels)
         (some? file-handler-channels)
         (some? event-access-channels)
         (some? rules-engine-channels)]}
  (let [client-in-channel (:in-channel channel-connection-channels)]
    (log/info (str "Dispatching Topic [" topic "], Sender [" sender "]"))
    (match [topic payload]
           [:command _]
           (do
             (log/info (str "Command " payload))
             ;; Results should be handled by "Result Rules Engine"
             ;; If a result is accepted it should be sent to the
             ;; "Results Access" for handling.
             (async/>!! (:in-channel rules-engine-channels)
                        {:topic   (first payload)
                         :payload (second payload)}))
           [:query _]
           (do
             (log/info (str "Query " payload))
             (async/>!! (:in-channel rules-engine-channels)
                        {:topic   (first (keys (first payload)))
                         :payload (first payload)}))
           [:file/import _]
           (let [[import import-ch] (async/alts!!
                                     [[(:in-channel file-handler-channels)
                                       message]
                                      (async/timeout 500)])]
             (when (nil? import)
               (async/put! client-in-channel (merge message
                                              {:topic :file/import
                                               :payload :time-out}))))
           [:file/imported _]
           (let [[tx tx-ch] (async/alts!!
                             [[(:in-channel event-access-channels)
                               (merge message
                                      {:topic :event-access/transact})]
                              (async/timeout 500)])]
             (when (nil? tx)
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
             (when (nil? tx)
               (merge message
                      {:topic :event-access/transaction-result :payload :time-out})))
           [:event-manager/query q]
           (let [[query query-ch] (async/alts!!
                                   [[(:in-channel event-access-channels)
                                     (merge message
                                            {:topic :event-access/query})]
                              (async/timeout 500)])]
             (when (nil? query)
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
             (when (nil? tx)
               (merge message
                      {:topic :event-manager/query-result :payload :time-out})))
           :else
           (let [[unknown ch] (async/alts!!
                               [[client-in-channel
                                 {:sender sender :topic :broker/unknown-topic :payload {:topic topic}}]
                                (async/timeout 500)])]
             (when (nil? unknown)
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
(defrecord MessageBroker [channel-connection-channels
                          file-handler-channels
                          event-access-channels
                          http-server-channels
                          datomic-storage-uri]
  component/Lifecycle
  (start [component]
    (log/report "Starting MessageBroker")
    (let [rules-in-ch (async/chan)
          rules-out-ch (async/chan)
          db (d/create-storage datomic-storage-uri (into d/select-activity-schema
                                                         (into d/application-schema
                                                               d/result-schema)))
          rules-engine (start-result-rules-engine
                         rules-in-ch
                         rules-out-ch
                         (:in-channel channel-connection-channels)
                         datomic-storage-uri)
          broker-process (start-message-process
                          message-dispatch 
                          {:channel-connection-channels channel-connection-channels
                           :file-handler-channels file-handler-channels
                           :event-access-channels event-access-channels
                           :http-server-channels http-server-channels
                           :rules-engine-channels {:in-channel rules-in-ch
                                                   :out-channel rules-out-ch}})]
      (assoc component :broker-process broker-process
                       :rules-engine rules-engine)))
  (stop [component]
    (log/report "Stopping MessageBroker")
    (assoc component :broker-process nil :file-handler-channels nil :event-access-channels nil
                     :rules-engine nil)))

(defn create-message-broker [datomic-uri]
  (map->MessageBroker {:datomic-storage-uri datomic-uri}))
