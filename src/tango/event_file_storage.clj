(ns tango.event-file-storage
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [tango.file-storage :as fs]))

(defn create-message [topic payload]
  {:topic topic
   :payload payload})

(defn- start-message-handler [in-channel out-channel]
  {:pre [(some? in-channel)
         (some? out-channel)]}
  (async/go-loop []
    (when-let [message (async/<! in-channel)]
      (log/debug (str "Raw message : " message))
      (when message
        (try
          (let [topic (:topic message)
                payload (:payload message)]
            (log/trace (str "Received: " message))
            (log/info (str "Received Topic: [" topic "]"))
            (match [topic payload]
                   [:event-file-storage/create p]
                   (async/put! out-channel (create-message :event-file-storage/created (fs/save [] p)))
                   [:event-file-storage/add p]
                   (let [current (fs/read-file "./file-storage/file-store.dat")
                         new-content (conj current p)]
                     (async/put! out-channel (merge message
                                                    {:topic :event-file-storage/added
                                                     :payload (fs/save new-content
                                                                       "./file-storage/file-store.dat")})))
                   [:event-file-storage/query p]
                   (let [raw-data (fs/read-file "./file-storage/file-store.dat")
                         q-result (select-keys raw-data p)]
                     (async/put! out-channel (merge message {:topic :event-file-storage/result
                                                             :payload q-result})))
                   
                   [:event-file-storage/ping p]
                   (async/put! out-channel (merge message {:topic :event-file-storage/pong}))
                   :else (async/>!!
                          out-channel
                          {:topic :event-file-storage/unkown-topic :payload {:topic topic}})))
          (catch Exception e
            (log/error e "Exception in message go loop")
            (async/>! out-channel (str "Exception message: " (.getMessage e)))))
        (recur)))))

(defrecord EventFileStorage [event-file-storage-channels message-handler]
  component/Lifecycle
  (start [component]
    (log/report "Starting EventFileStorage")
    (if (and event-file-storage-channels message-handler)
      component
      (assoc component :message-handler (start-message-handler
                                         (:in-channel event-file-storage-channels)
                                         (:out-channel event-file-storage-channels)))))
  (stop [component]
    (log/report "Stopping EventFileStorage")
    (assoc component :message-handler nil :event-file-storage-channels nil)))

(defn create-event-file-storage []
  (map->EventFileStorage {}))

(defrecord EventFileStorageChannels [in-channel out-channel]
   component/Lifecycle
  (start [component]
    (if (and in-channel out-channel)
      component
      (do
        (log/info "Starting EventFileStorage Channels")
        (assoc component
          :in-channel (async/chan)
          :out-channel (async/chan)))))
  (stop [component]
    (log/info "Closing EventFileStorage Channels")
    (if-let [in-chan (:in-channel component)]
      (async/close! in-chan))
    (if-let [out-chan (:out-channel component)]
      (async/close! out-chan))
    (assoc component
      :in-channel nil
      :out-channel nil)))

(defn create-event-file-storage-channels []
  (map->EventFileStorageChannels {}))
