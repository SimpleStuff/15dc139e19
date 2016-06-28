(ns tango.event-access
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [tango.datomic-storage :as d]))

(defn- start-message-handler [in-channel out-channel conn
                              ]
  {:pre [(some? in-channel)
         (some? out-channel)
         ;(some? storage-channels)
         ]}
  (async/go-loop []
    (when-let [message (async/<! in-channel)]
      (log/debug (str "Raw message : " message))
      (when message
        (try
          (let [topic (:topic message)
                payload (:payload message)
                ;storage-out (:out-channel storage-channels)
                ]
            (log/trace (str "Received: " message))
            (log/info (str "Received Topic: [" topic "]"))
            (match [topic payload]
                   [:event-access/create-competition p]
                   (let [_ (d/create-competition conn p)]
                     (async/put! out-channel {:topic :tx/processed :payload topic}))
                   [:event-access/create-class p]
                   (if (:competition/id p)
                     (let [_ (d/create-class conn (:competition/id p) (:competition/class p))]
                       (async/put! out-channel {:topic :tx/processed :payload topic}))
                     (async/put! out-channel {:topic :tx/rejected :payload {:reason :invalid-argument
                                                                            :message "Missing competition/id"}}))
                   [:event-access/query-competition p]
                   (let [result (d/query-competition conn (:query p))]
                     (async/put! out-channel {:topic :tx/processed :payload {:topic topic
                                                                             :result result}}))
                   ;[:event-access/transact t]
                   ;(let [[tx c] (async/alts! [[(:in-channel storage-channels)
                   ;                             {:topic :event-file-storage/transact
                   ;                              :payload t}]
                   ;                            (async/timeout 2000)])]
                   ;  (if tx
                   ;    (let [[tx-result ch] (async/alts! [storage-out (async/timeout 2000)])]
                   ;      (if tx-result
                   ;        (async/put! out-channel (merge message
                   ;                                       {:topic :event-access/transaction-result
                   ;                                        :payload (:payload tx-result)}))
                   ;        (async/put! out-channel (merge message
                   ;                                        {:topic :event-access/transaction-result-timeout
                   ;                                         :payload :time-out}))))))
                   ;[:event-access/query q]
                   ;;; send query to storage or timeout
                   ;(let [[v c] (async/alts! [[(:in-channel storage-channels)
                   ;                           {:topic :event-file-storage/query
                   ;                            :payload q}]
                   ;                          (async/timeout 2000)])]
                   ;  ;; if the query was picked up, wait for the result, else timeout
                   ;  (if v
                   ;    (let [[result ch] (async/alts! [storage-out (async/timeout 2000)])]
                   ;      (if result
                   ;        (async/put! out-channel (merge message
                   ;                                       {:topic :event-access/query-result
                   ;                                        :payload (:payload result)}))
                   ;        (async/put! out-channel (merge message
                   ;                                       {:topic :event-file-storage/query-result-timeout
                   ;                                        :payload :time-out}))))
                   ;    (async/put! out-channel (merge message
                   ;                                   {:topic :event-file-storage/query-timeout
                   ;                                    :payload :time-out}))))

                   [:event-access/ping p]
                   (async/put! out-channel (merge message {:topic :event-access/pong}))
                   :else (async/>!!
                          out-channel
                          {:topic :tx/rejected
                           :payload {:reason :event-access/unkown-topic
                                     :message message}})))
          (catch Exception e
            (log/error e "Exception in message go loop")
            (async/>! out-channel
                      {:topic   :tx/rejected
                       :payload {:reason  :event-access/exception
                                 :message (str "Exception message: " (.getMessage e))}})))
        (recur)))))

(defrecord EventAccess [event-access-channels message-handler ;storage-channels
                        datomic-uri
                        schema-path]
  component/Lifecycle
  (start [component]
    (log/report "Starting EventAccess")
    (let [schema-tx (read-string (slurp (clojure.java.io/resource schema-path)))
          _ (d/create-storage datomic-uri schema-tx)]
      (if (and event-access-channels message-handler)
        component
        (assoc component :message-handler (start-message-handler
                                            (:in-channel event-access-channels)
                                            (:out-channel event-access-channels)
                                            (d/create-connection datomic-uri)
                                            )))))
  (stop [component]
    (log/report "Stopping EventAccess")
    (assoc component :message-handler nil :event-access-channels nil :datomic-uri nil :schema-path nil)))

(defn create-event-access [datomic-resource-uri schema-path]
  (map->EventAccess {:datomic-uri datomic-resource-uri
                     :schema-path schema-path}))

(defrecord EventAccessChannels [in-channel out-channel]
   component/Lifecycle
  (start [component]
    (if (and in-channel out-channel)
      component
      (do
        (log/info "Starting EventAccess Channels")
        (assoc component
          :in-channel (async/chan)
          :out-channel (async/chan)))))
  (stop [component]
    (log/info "Closing EventAccess Channels")
    (if-let [in-chan (:in-channel component)]
      (async/close! in-chan))
    (if-let [out-chan (:out-channel component)]
      (async/close! out-chan))
    (assoc component
      :in-channel nil
      :out-channel nil)))

(defn create-event-access-channels []
  (map->EventAccessChannels {}))

