(ns tango.import-engine
  (:require [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [tango.import :as import]
            [clojure.core.match :refer [match]]
            [tango.datomic-storage :as d]
            [tango.generate-recalled :as gen]))

;; TODO - default to #(java.util.UUID/randomUUID)
(defn- start-message-handler [in-channel out-channel {:keys [id-generator-fn datomic-uri]}]
  {:pre [(some? in-channel)
         (some? out-channel)
         (some? id-generator-fn)]}
  (async/go-loop []
    (when-let [message (async/<! in-channel)]
      (log/debug (str "Raw message : " message))
      (when message
        (try
          (let [topic (:topic message)
                payload (:payload message)]
            (log/trace (str "Files received: " message))
            (log/info (str "Received Topic: [" topic "]"))
            (match [topic payload]
                   [:file/import p]
                   (do
                     ;; TODO - HAXX, fix this for realz
                     (log/info "Clear DB")
                     (d/delete-storage datomic-uri)
                     (d/create-storage datomic-uri (into d/select-activity-schema
                                                         (into d/application-schema
                                                               d/result-schema)))
                     (log/info "DB Clear")
                     ;; TODO - verify all indata i.e. p needs a :content here
                     (let [import-result (import/import-file-stream
                                           (:content p)
                                           id-generator-fn)
                           recalled-html (gen/generate-recalled-html import-result)
                           ;; TODO - filter known
                           ;; - read filtered from file
                           ;; - filter recalled-html on activity number
                           ;; - conj and save new known generated numbers
                           ;; - map spit on filtered recalled
                           ]
                       (do
                         ;; Insert code here for now
                         ;; use (log/info to find problems, check that core.clj :log-level is
                         ;; as expected
                         (spit "recalled-html.txt" recalled-html)
                         (log/info recalled-html)
                         (async/put! out-channel (merge message {:topic   :file/imported
                                                                 :payload import-result})))))
                   [:file/ping p]
                   (async/put! out-channel (merge message {:topic :file/pong}))
                   :else (async/>!!
                          out-channel
                          {:topic :files/unkown-topic :payload {:topic topic}})))
          (catch Exception e
            (log/error e "Exception in Files message go loop")
            (async/>! out-channel (str "Exception message: " (.getMessage e)))))
        (recur)))))


(defrecord FileHandler [file-handler-channels message-handler id-generator-fn datomic-uri]
  component/Lifecycle
  (start [component]
    (log/report "Starting FileHandler")
    (if (and file-handler-channels message-handler)
      component
      (let [message-handler
            (start-message-handler (:in-channel file-handler-channels)
                                   (:out-channel file-handler-channels)
                                   {:id-generator-fn id-generator-fn
                                    :datomic-uri datomic-uri})]
        (assoc component :message-handler message-handler))))
  (stop [component]
    (log/report "Stopping FileHandler")
    (assoc component :message-handler nil :file-handler-channels nil)))

(defn create-file-handler [id-generator-fn datomic-uri]
  (map->FileHandler {:id-generator-fn id-generator-fn
                     :datomic-uri datomic-uri}))

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
    (if-let [out-chan (:out-channel component)]
      (async/close! out-chan))
    (assoc component
      :in-channel nil
      :out-channel nil)))

(defn create-file-handler-channels []
  (map->FileHandlerChannels {}))
