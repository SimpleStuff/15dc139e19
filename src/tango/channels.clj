;; ## Tango core.async clients component
;; 'tango.channels' provides core.async in/out channels for clients to
;; send and receive messages on. This is primarely of interest for
;; in-memory clients.
(ns tango.channels
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn- create-exception-message [e]
  (str "Exception message: " (.getMessage e)))

(defn- log-raw-message [message]
  (log/debug (str "Raw message : " message)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Component

(defn start-message-handler
  [name in-channel out-channel error-channel]
  (async/go-loop []
    (when-let [message (async/<! in-channel)]
      (log-raw-message message)
      (when message
        (try
          (let [topic (:topic message)
                payload (:payload message)]
            (log/trace (str name " received: " message))
            (async/put! out-channel message))
          (catch Exception e
            (log/error e (str "Exception in " name " message go loop"))
            (async/>! error-channel (create-exception-message e))))
        (recur)))))

(defrecord ChannelConnection [channel-connection-channels in-channel out-channel message-handler]
  component/Lifecycle
  (start [component]
    (log/report "Starting ChannelConnection")
    (if (and channel-connection-channels message-handler)
      component
      (let [clients-in-channel (async/chan)
            clients-out-channel (async/chan)
            message-handler (start-message-handler
                             "ChannelConnection"
                             (:in-channel channel-connection-channels)
                             clients-out-channel
                             (:out-channel channel-connection-channels))
            clients-handler (start-message-handler
                             "Client ChannelConnection"
                             (:out-channel channel-connection-channels)
                             clients-in-channel
                             clients-in-channel)]
        (assoc component
          :message-handler message-handler
          :in-channel clients-in-channel
          :out-channel clients-out-channel))))
  (stop [component]
    (log/report "Stopping ChannelConnection")
    (assoc component :message-handler nil :in-channel nil :out-channel nil)))

(defn create-channel-connection []
  (map->ChannelConnection {}))

(defrecord ChannelConnectionChannels [in-channel out-channel]
  component/Lifecycle
  (start [component]
    (if (and in-channel out-channel)
      component
      (do
        (log/info "Starting ChannelConnection Channels")
        (assoc component
          :in-channel (async/chan)
          :out-channel (async/chan)))))
  (stop [component]
    (log/info "Closing ChannelConnection Channels")
    (if-let [in-chan (:in-channel component)]
      (async/close! in-chan))
    (if-let [out-chan (:out-channel component)]
      (async/close! out-chan))
    (assoc component
      :in-channel nil
      :out-channel nil)))

(defn create-channel-connection-channels []
  (map->ChannelConnectionChannels {}))
