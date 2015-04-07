(ns tango.messaging
  (:require [tango.web-socket :as ws]
            [tango.import :as import]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [clojure.core.match :refer [match]]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

(defn find-handler [handler-k handler-map]
  (log/info (str "Find handler for [" handler-k "]"))
  (let [handler (get handler-map handler-k)]
    (if handler
      (do
        (log/info (str "Found handler [" handler-k "]"))
        handler)
      (log/info (str "Failed to find handler [" handler-k "]")))))

;; Match incoming client messages and translate to server side format with for example
;; the client id to be able to send responses async to the correct client.
;; The handler-map contains handlers that produce the result that should be sent back to the client.
;; TODO - fix an error pattern so that the client receives the errors and our message loop do not die.
(defn message-dispatch [handler-map {:keys [topic payload sender]} ]
  (match [topic payload]
         [:file/import xml]
         {:id sender :message [:file/imported {:content ((find-handler :file-import handler-map) xml)}]}
         [:file/export _]
         (let [handler (find-handler :file-export handler-map)]
           (if handler
             {:id sender :message [:file/export
                                   {:content
                                    (handler (:dance-perfect/version (:file/content payload))
                                             (:file/content payload))}]}
             {:id sender :message [:server/error {:error/message "Failed to export file"}]}))
         [:client/ping p]
         {:id sender :message [:server/pong []]}
         :else {:id sender :message [:client/unkown-topic {:topic topic}]}))

(defn create-message-dispatch [handler-map]
  (partial message-dispatch handler-map))

(defn- create-exception-message [e]
  (str "Exception message: " (.getMessage e)))

;; TODO - refactor into generic message loop that runs body
(defn start-message-loop [dispatch-fn messages-receive-ch messages-send-ch system-ch]
  (async/go-loop []
    (when-let [msg (async/<! messages-receive-ch)]
      (try
        (when msg
          (log/info (str "Message: [" (:topic msg) "] from sender id: " (:sender msg)))
          (log/trace (str "Payload: " (:payload msg)))
          (async/>! messages-send-ch (dispatch-fn msg)))
        (catch Exception e
          (log/error e "Exception in message receive go loop")
          (async/>! system-ch (create-exception-message e))))
      (recur))))

(defn start-message-send-loop [send-fn messages-send-chan system-ch]
  (async/go-loop []
    (when-let [msg (async/<! messages-send-chan)]
      (try
        (when msg
          (log/info (str "Sending: [" (first (:message msg)) "] to client id: " (:id msg)))
          (log/trace (str "full message " (:message msg)))
          (send-fn msg))
        (catch Exception e
          (log/error e "Exception in message send go loop")
          (async/>! system-ch (create-exception-message e))))
      (recur))))

(defn start-message-looper [])

(defrecord MessageHandler [dispatch-fn ws-connection channels database]
  component/Lifecycle
  (start [component]
    (log/info "Starting Message Handler")
    (let [msg-send (start-message-send-loop (fn [msg] (ws/send-socket! ws-connection (:id msg) (:message msg)))
                                            (:messages-send-chan channels) (:system-chan channels))
          msg-rec (start-message-loop
                   (partial message-dispatch {:file-import #(import/import-file-stream %)
                                              :file-export
                                              #(spit (str "resources/public/exported-files/"
                                                          (:competition/name %2)
                                                          ".xml")
                                                     (import/data->dance-perfect-xml %1 %2))})
                   (:messages-receive-chan channels) (:messages-send-chan channels) (:system-chan channels))]
      (assoc component :message-sender msg-send :message-receiver msg-rec)))

  (stop [component]
    (log/info "Stopping Message Handling")
    (assoc component :message-sender nil :message-receiver nil)))

(defn create-message-handler []
  (map->MessageHandler {}))
