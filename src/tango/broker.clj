(ns tango.broker
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

(defn create-dispatch-components [channels-connection-channels
                                  import-handler-channels
                                  persistance-channels]
  {:channels channels-connection-channels
   :import-handler import-handler-channels
   :persistance persistance-channels})

(defn message-dispatch [{:keys [topic payload sender] :as message} components]
  (match [topic payload]
         [:import/file file]
         (async/>!! (:in-channel (:import-handler components)) message)
         :else (async/>!! (:in-channel (:channels components))
                          {:sender sender :topic :broker/unkown-topic :payload {:topic topic}})))

(defn start-message-process [dispatch-fn components-m]
  (let [out-chans (into [] (map :out-channel (vals components-m)))]
    (log/info (str "Channels " out-chans))
    (async/go-loop []
      (when-let [[message _] (async/alts! out-chans) ]
        (try
          (when message
            (log/info (str "Broker received : " message))
            (async/thread (dispatch-fn message components-m)))
          (catch Exception e
            (log/error e "Exception in MessageBroker router loop")))
        (recur)))))

;; TODO - need to fix som pub/sub pattern
(defrecord MessageBroker [channel-connection-channels import-handler-channels persistance-channels]
  component/Lifecycle
  (start [component]
    (log/info "Starting MessageBroker")
    (let [broker-process (start-message-process
                          message-dispatch
                          (create-dispatch-components channel-connection-channels
                                                      import-handler-channels
                                                      persistance-channels))]
      (assoc component :broker-process broker-process)))
  (stop [component]
    (log/info "Stopping MessageBroker")
    (assoc component :broker-process nil)))

(defn create-message-broker []
  (map->MessageBroker {})) 
