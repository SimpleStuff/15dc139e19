;; ## Tango web-socket clients component
;; 'tango.web-socket' provides support for clojurescript clients
;; running 'sente' to connect to the system via web-socket.
(ns tango.web-socket
  (:require [taoensso.sente :as sente]
            [taoensso.sente.server-adapters.http-kit :refer (sente-web-server-adapter)]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn- create-exception-message [e]
  (str "Exception message: " (.getMessage e)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente web-socket implementation

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

(defn event-msg-handler*
  "The message handler registerd to receive messages from sente.
  Messages are adapted to an internal format before they are put on this
  components out-channel."
  [out-channel {:as ev-msg :keys [id ?data event ring-req]}]
  (let [adapted (sente-ws-bus-adapter-in ev-msg)]
    (log/trace "Web Socket receive: " ev-msg)
    (log/trace (str "Receive Adapted: " adapted))
    (log/info (str "Web Socket received message from client : [" id " " event "]"))
    (async/>!! out-channel adapted)
    (log/trace (str "Web Socket sent message to out channel: " adapted))))

(defn start-message-send-loop 
  "Message loop that will listen on this components in-channel.
  Any message received will be adapted to sente format and then sent
  using sentes send funtion."
  [send-fn messages-send-chan system-ch out-adapter-fn]
  (async/go-loop []
    (when-let [msg (async/<! messages-send-chan)]
      (log/debug (str "Raw message : " msg))
      (when msg
        (try
          (let [adapted (out-adapter-fn msg)]
            (log/debug (str "Sending: " msg))
            (log/debug (str "Adapted: " adapted))
            (send-fn adapted))
          (catch Exception e
            (log/error e "Exception in Web-socket message send go loop")
            ;; TODO - do we want a system channel or should it be sent to broker?
            ;;(async/>! system-ch (create-exception-message e))
            ))
        (recur)))))

(defrecord WSRingHandlers [ajax-post-fn ajax-get-or-ws-handshake-fn])

(defrecord WSConnection [ch-recv connected-uids send-fn ring-handlers ws-connection-channels]
  component/Lifecycle
  (start [component]
    (if (and ch-recv connected-uids send-fn ring-handlers)
      component
      (let [{:keys [ch-recv send-fn connected-uids
                    ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (sente/make-channel-socket! sente-web-server-adapter {})]
        (log/report "Start web socket")
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
                      sente-ws-bus-adapter-out)))))
  (stop [component]
    (log/report "Stop web socket")
    ((:stop-the-thing component))
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
    (if-let [out-chan (:out-channel component)]
      (async/close! out-chan))
    (assoc component
          :in-channel nil
          :out-channel nil)))

(defn create-ws-channels []
  (map->WSConnectionChannels {}))


