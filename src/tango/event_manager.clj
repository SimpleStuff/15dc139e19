(ns tango.event-manager
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Process

(defn message-routing [{:keys [topic payload sender] :as message}
                       {:keys [client-channels
                               event-manager-channels] :as components}]
  {:pre [(some? client-channels)
         (some? event-manager-channels)]}
  ;(let [client-in-channel (:in-channel client-channels)])
  (log/info (str "Dispatching Topic [" topic "], Sender [" sender "]"))
  (condp = topic
    :command (async/>!! (:out-channel event-manager-channels) :command)

    ))



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

(defn- start-message-process [in-channel out-channel event-access]
  {:pre [(some? in-channel)
         (some? out-channel)]}
  (async/go-loop []
    (log/info "Waiting for message..")
    (when-let [message (async/<! in-channel)]
      (log/debug (str "Raw message : " message))
      (when message
        (try
          (let [topic (:topic message)
                payload (:payload message)]
            (log/trace (str "Received: " message))
            (log/info (str "Received Topic: [" topic "]"))
            (match [topic payload]
                   [:event-manager/create-competition p]
                   (let [[v ch] (async/alts! [[(:in-channel event-access)
                                               (merge message {:topic :event-access/create-competition})]
                                              (async/timeout 2000)])]
                     (if v
                       (let [[result ch] (async/alts! [(:out-channel event-access)])]
                         (if result
                           (if (= :tx/rejected (:topic result))
                             (async/put! out-channel (merge message
                                                            {:topic :tx/rejected
                                                             :payload (:payload result)}))
                             (async/put! out-channel (merge message
                                                            {:topic   :event-manager/tx-processed
                                                             :payload {:topic :event-manager/create-competition}})))
                           (async/put! out-channel (merge message
                                                          {:topic :event-manager/tx-timout
                                                           :payload {:reason :event-access/timeout}}))))))
                   [:event-manager/create-class p]
                   (let [[v ch] (async/alts! [[(:in-channel event-access)
                                               (merge message {:topic :event-access/create-class})]
                                              (async/timeout 2000)])]
                     (if v
                       (let [[result ch] (async/alts! [(:out-channel event-access)
                                                       (async/timeout 500)])]
                         (log/info "Create class Event Access answer")
                         (if result
                           (if (= :tx/rejected (:topic result))
                             (async/put! out-channel (merge message
                                                            {:topic :tx/rejected
                                                             :payload (:payload result)}))
                             (async/put! out-channel (merge message
                                                            {:topic   :event-manager/tx-processed
                                                             :payload {:topic :event-manager/create-class}})))
                           (async/put! out-channel (merge message
                                                          {:topic :event-manager/tx-timout
                                                           :payload {:reason :event-access/timeout}}))))))

                   [:event-manager/query-competition p]
                   (let [[v ch] (async/alts! [[(:in-channel event-access)
                                               (merge message {:topic :event-access/query-competition})]
                                              (async/timeout 2000)])]
                     (if v
                       (let [[result ch] (async/alts! [(:out-channel event-access)])]
                         (if result
                           (if (= :tx/rejected (:topic result))
                             (async/put! out-channel (merge message
                                                            {:topic :tx/rejected
                                                             :payload (:payload result)}))
                             (async/put! out-channel (merge message
                                                            {:topic   :event-manager/tx-processed
                                                             :payload {:result (:result (:payload result))
                                                                       :topic :event-manager/query-competition}})))
                           (async/put! out-channel (merge message
                                                          {:topic :event-manager/tx-timout
                                                           :payload {:reason :event-access/timeout}}))))))
                   [:event-manager/ping p]
                   (async/put! out-channel (merge message {:topic :event-access/pong}))
                   :else (async/>!!
                           out-channel
                           {:topic :tx/rejected
                            :payload {:reason :event-manager/unkown-topic
                                      :message message}}))
            )
          (catch Exception e
            (log/error e "Exception in message go loop")
            (async/>! out-channel (str "Exception message: " (.getMessage e)))))
        (recur)))))

(defrecord EventManager [event-manager-channels event-access-channels]
  component/Lifecycle
  (start [component]
    (log/report "Starting EventManager")
    (let [event-manager-process (start-message-process
                                  ;message-routing
                                  (:in-channel event-manager-channels)
                                  (:out-channel event-manager-channels)
                                  event-access-channels)]
      (assoc component :event-manager-process event-manager-process)))
  (stop [component]
    (log/report "Stopping EventManager")
    (assoc component :event-manager-channels nil)))

(defn create-event-manager []
  (map->EventManager {}))

(defrecord EventManagerChannels [in-channel out-channel]
  component/Lifecycle
  (start [component]
    (if (and in-channel out-channel)
      component
      (do
        (log/info "Starting EventManager Channels")
        (assoc component
          :in-channel (async/chan)
          :out-channel (async/chan)))))
  (stop [component]
    (log/info "Closing EventManager Channels")
    (if-let [in-chan (:in-channel component)]
      (async/close! in-chan))
    (if-let [out-chan (:out-channel component)]
      (async/close! out-chan))
    (assoc component
      :in-channel nil
      :out-channel nil)))

(defn create-event-manager-channels []
  (map->EventManagerChannels {}))