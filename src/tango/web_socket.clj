(ns tango.web-socket
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

(defn sente-ws-bus-adapter-out
  "Convert a internal message to a sente socket event :
  [user-id event] where event [<ev-id> <?ev-data>], 
  e.g. [:my-app/some-req {:data data}]"
  [{:keys [topic payload sender]}]
  (let [sender-id (if sender sender :sente/all-users-without-uid)]
    [sender-id [topic payload]]))

(defn sente-ws-bus-adapter-in
  "Convert a sente web-socket message to internal message format.
  Some special handling are needed for all sente specific messages"
  [{:keys [id ?data event ring-req] :as ev-msg }]
  (let [adapted-topic
        (cond
         (= id :chsk/uidport-open) :client/connect
         :default id)]
    {:topic adapted-topic :payload ?data :sender (:uid (:session ring-req))}))

(defn event-msg-handler* [out-channel {:as ev-msg :keys [id ?data event ring-req]}]
  (let [adapted (sente-ws-bus-adapter-in ev-msg)]
    (log/trace "Web Socket receive: " ev-msg)
    (log/info (str "Receive Adapted: " adapted))
    (async/>!! out-channel adapted)
    (log/info (str "Web Socket sent message to out channel: " adapted))))

;; (defn send-socket! [ws-connection user-id event]
;;   ((:send-fn ws-connection) user-id event))

(defn- create-exception-message [e]
  (str "Exception message: " (.getMessage e)))

(defn start-message-send-loop [send-fn messages-send-chan system-ch out-adapter-fn]
  (async/go-loop []
    (when-let [msg (async/<! messages-send-chan)]
      (try
        (when msg
          (let [adapted (out-adapter-fn msg)]
            (log/trace (str "full message " msg))
            (log/info (str "Sending: " msg))
            (log/info (str "Adapted: " adapted))
            (send-fn adapted)))
        (catch Exception e
          (log/error e "Exception in Web-socket message send go loop")
          (async/>! system-ch (create-exception-message e))))
      (recur))))

(defrecord WSRingHandlers [ajax-post-fn ajax-get-or-ws-handshake-fn])

(defrecord WSConnection [ch-recv connected-uids send-fn ring-handlers ws-connection-channels]
  component/Lifecycle
  (start [component]
    (if (and ch-recv connected-uids send-fn ring-handlers)
      component
      (let [{:keys [ch-recv send-fn connected-uids
                    ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (sente/make-channel-socket! sente-web-server-adapter {})]
        (log/info "Start web socket")
        (assoc component
          :ch-recv ch-recv
          :connected-uids connected-uids
          :send-fn send-fn
          :stop-the-thing 
          (sente/start-chsk-router! ch-recv (partial event-msg-handler* (:out-channel ws-connection-channels)))
          :ring-handlers (->WSRingHandlers ajax-post-fn ajax-get-or-ws-handshake-fn)
          :send-loop (start-message-send-loop
                      (fn [[user-id event]]
                        (send-fn user-id event))
                      (:in-channel ws-connection-channels)
                      (async/chan)
                                        ;(:system-chan channels)
                      sente-ws-bus-adapter-out)))))
  (stop [component]
    (log/info "Stop web socket")
    (:stop-the-thing component)
    (assoc component
      :ch-recv nil :connected-uids nil :send-fn nil :ring-handlers nil)))

(defn create-ws-connection []
  (map->WSConnection {}))

(defrecord WSConnectionChannels [in-channel out-channel]
  component/Lifecycle
  (start [component]
    (if (and in-channel out-channel)
      component
      (do
        (log/info "Starting WSConnection Channels")
        (assoc component
          :in-channel (async/chan)
          :out-channel (async/chan)))))
  (stop [component]
    (log/info "Closing WSConnection Channels")
    (if-let [in-chan (:in-channel component)]
      (async/close! in-chan))
    (if-let [out-chan (:in-channel component)]
      (async/close! out-chan))
    (assoc component
          :in-channel nil
          :out-channel nil)))

(defn create-ws-channels []
  (map->WSConnectionChannels {}))


