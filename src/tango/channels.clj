(ns tango.channels
  (:require [com.stuartsierra.component :as component]
            [clojure.core.async :as async]))

(defrecord Channels [messages-receive-chan messages-send-chan system-chan]
  component/Lifecycle
  (start [component]
    (if (and messages-receive-chan messages-send-chan system-chan)
      component
      (do
        (println "Open Channels")
        (assoc component
          :messages-receive-chan (async/chan)
          :messages-send-chan (async/chan)
          :system-chan (async/chan)))))
  (stop [component]
    (println "Closing Channels")
    (if-let [rec-ch (:messages-receive-chan component)]
      (async/close! rec-ch))
    (if-let [send-ch (:messages-send-chan component)]
      (async/close! send-ch))
    (if-let [sys-ch (:system-chan component)]
      (async/close! sys-ch))
    (assoc component
        :messages-receive-chan nil
        :messages-send-chan nil
        :system-chan nil)))

(defn create-channels []
  (map->Channels {}))
