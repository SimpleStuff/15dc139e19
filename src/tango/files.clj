(ns tango.files
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]))

(defn start-message-handler [in-channel out-channel]
  (async/go-loop []
    (when-let [message (async/<! in-channel)]
      (try
        (when message
          (let [topic (:topic message)
                payload (:payload message)]
            (log/trace (str "Files received: " message))
            (match [topic payload]
                   [:import/dummy p]
                   (async/put! out-channel (merge message {:topic :import/verified :payload p}))
                   :else (async/>!! out-channel {:topic :files/unkown-topic :payload {:topic topic}}))))
        (catch Exception e
          (log/error e "Exception in Files message go loop")
          (async/>! out-channel (str "Exception message: " (.getMessage e)))))
      (recur))))

(defrecord FileHandler [file-handler-channels message-handler]
  component/Lifecycle
  (start [component]
    (if (and file-handler-channels message-handler)
      component
      (let [message-handler (start-message-handler (:in-channel file-handler-channels)
                                                   (:out-channel file-handler-channels))]
        (assoc component :message-handler message-handler))))
  (stop [component]
    (assoc component :message-handler nil)))

(defn create-file-handler []
  (map->FileHandler {}))

(defrecord FileHandlerChannels [in-channel out-channel]
  component/Lifecycle
  (start [component]
    (if (and in-channel out-channel)
      component
      (do
        (log/info "Starting FileHandler Channels")
        (assoc component
          :in-channel (async/chan)
          :out-channel (async/chan)))))
  (stop [component]
    (log/info "Closing FileHandler Channels")
    (if-let [in-chan (:in-channel component)]
      (async/close! in-chan))
    (if-let [out-chan (:in-channel component)]
      (async/close! out-chan))
    (assoc component
      :in-channel nil
      :out-channel nil)))

(defn create-file-handler-channels []
  (map->FileHandlerChannels {}))
