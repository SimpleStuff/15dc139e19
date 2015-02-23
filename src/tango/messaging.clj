(ns tango.messaging
  (:require [tango.web-socket :as ws]
            [tango.import :as import]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [clojure.core.match :refer [match]]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

(defn message-dispatch [handler-map {:keys [topic payload sender]} ]
  (match [topic payload]
         [:file/import xml]
         {:id sender :message [:file/imported {:content ((:file-import handler-map) xml)}]}
         [:client/ping p]
         {:id sender :message [:server/pong []]}
         :else {:id sender :message [:client/unkown-topic {:topic topic}]}))

(defn create-message-dispatch [handler-map]
  (partial message-dispatch handler-map))

(defn start-message-loop [dispatch-fn messages-receive-ch messages-send-ch system-ch]
  (async/go-loop []
    (when-let [msg (async/<! messages-receive-ch)]
      (try
        (if msg
          (do
            (log/info (str "Message: [" (:topic msg) "] from sender id: " (:sender msg)))
            (log/trace (str "Payload: " (:payload msg)))
            (async/>! messages-send-ch (dispatch-fn msg))))
        (catch Exception e
          (log/error e "Exception in message receive go loop")
          (async/>! system-ch (str "Exception message: " (.getMessage e)))))
      (recur))))

(defn start-message-send-loop [send-fn messages-send-chan system-ch]
  (async/go-loop []
    (when-let [msg (async/<! messages-send-chan)]
      (try
        (if msg
          (do
            (log/info (str "Sending: [" (first (:message msg)) "] to client id: " (:id msg)))
            (log/trace (str "full message " (:message msg)))
            (send-fn msg)))
        (catch Exception e
          (log/error e "Exception in message send go loop")
          (async/>! system-ch (str "Exception message: " (.getMessage e)))))
      (recur))))

(defrecord MessageHandler [dispatch-fn ws-connection channels database]
  component/Lifecycle
  (start [component]
    (log/info "Starting Message Handler")
    (let [msg-send (start-message-send-loop (fn [msg] (ws/send-socket! ws-connection (:id msg) (:message msg)))
                                            (:messages-send-chan channels) (:system-chan channels))
          msg-rec (start-message-loop
                   (partial message-dispatch {:file-import #(import/import-file-stream %)})
                   (:messages-receive-chan channels) (:messages-send-chan channels) (:system-chan channels))]
      (assoc component :message-sender msg-send :message-receiver msg-rec)))

  (stop [component]
    (log/info "Stopping Message Handling")
    (assoc component :message-sender nil :message-receiver nil)))

(defn create-message-handler []
  (map->MessageHandler {}))
