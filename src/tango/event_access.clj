(ns tango.event-access
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]))

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
                   ;; [:file/import p]
                   ;; ;; TODO - verify all indata i.e. p needs a :content here
                   ;; (async/put! out-channel (merge message {:topic :file/imported
                   ;;                                         :payload (import/import-file-stream
                   ;;                                                   (:content p)
                   ;;                                                   id-generator-fn)}))
                   [:event-access/ping p]
                   (async/put! out-channel (merge message {:topic :event-access/pong}))
                   :else (async/>!!
                          out-channel
                          {:topic :event-access/unkown-topic :payload {:topic topic}})))
          (catch Exception e
            (log/error e "Exception in message go loop")
            (async/>! out-channel (str "Exception message: " (.getMessage e)))))
        (recur)))))

(defrecord EventAccess [event-access-channels message-handler]
  component/Lifecycle
  (start [component]
    (log/report "Starting EventAccess")
    (if (and event-access-channels message-handler)
      component
      (assoc component :message-handler (start-message-handler
                                         (:in-channel event-access-channels)
                                         (:out-channel event-access-channels)))))
  (stop [component]
    (log/report "Stopping EventAccess")
    (assoc component :message-handler nil :event-access-channels nil)))

(defn create-event-access []
  (map->EventAccess {}))

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

