(ns tango.web-socket
  (:require [taoensso.sente :as sente]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

(defn event-msg-handler* [messages-receive-chan {:as ev-msg :keys [id ?data event ring-req]}]
  (async/>!! messages-receive-chan {:topic id :payload ?data :sender (:uid (:session ring-req))}))

(defrecord WSRingHandlers [ajax-post-fn ajax-get-or-ws-handshake-fn])

(defrecord WSConnection [ch-recv connected-uids send-fn ring-handlers channels]
  component/Lifecycle
  (start [component]
    (if (and ch-recv connected-uids send-fn ring-handlers)
      component
      (let [{:keys [ch-recv send-fn connected-uids
                    ajax-post-fn ajax-get-or-ws-handshake-fn]}
            (sente/make-channel-socket!)]
        (println "Start WS")
        (assoc component
          :ch-recv ch-recv
          :connected-uids connected-uids
          :send-fn send-fn
          :stop-the-thing 
          (sente/start-chsk-router! ch-recv (partial event-msg-handler* (:messages-receive-chan channels)))
          :ring-handlers (->WSRingHandlers ajax-post-fn ajax-get-or-ws-handshake-fn)))))
  (stop [component]
    (println "Stop WS")
    (:stop-the-thing component)
    (assoc component
      :ch-recv nil :connected-uids nil :send-fn nil :ring-handlers nil)))

(defn create-ws-connection []
  (map->WSConnection {}))

(defn send-socket! [ws-connection user-id event]
  ((:send-fn ws-connection) user-id event))
