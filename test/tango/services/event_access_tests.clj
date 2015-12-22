(ns tango.services.event-access-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.event-access :as ea]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn create-test-service []
  (assoc
      (ea/create-event-access)
    :event-access-channels
    (component/start (ea/create-event-access-channels))))

(defn- send-to [service message]
  (async/>!! (:in-channel (:event-access-channels service)) message))

(defn- receive-from [service]
  (async/<!! (:out-channel (:event-access-channels service))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of event access

(deftest access-stored-event
  (testing "Access a stored event"
    (let [event-access (component/start (create-test-service))]
      (send-to event-access {:topic :event-access/query :payload [:competition/name]})
      (is (= {:topic :event-access/events :payload [{:competition/name "TurboMega Tävling"}]}
             (receive-from event-access))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service tests

(deftest instantiate-import-engine
  (testing "Instansiate event-access service"
    (let [event-access (ea/create-event-access)
          event-access-channels (ea/create-event-access-channels)]
      (is (= tango.event_access.EventAccess (class event-access)))
      (is (= tango.event_access.EventAccessChannels (class event-access-channels))))))

(deftest service-life-cycle
  (testing "Service life cycle"
    (let [event-access (component/start (create-test-service))
          stopped-event-access (component/stop event-access)]
      (is (= 2 (count (keys event-access))))

      (is (not= nil (:event-access-channels event-access)))
      (is (not= nil (:message-handler event-access)))

      (is (= nil (:event-access-channels stopped-event-access)))
      (is (= nil (:message-handler stopped-event-access))))))

(deftest system-component-properties
  (testing "Service is a well behavied system service"
    (let [event-access (component/start (create-test-service))]
      (send-to event-access {:topic :event-access/ping})
      (is (= {:topic :event-access/pong}
             (receive-from event-access)))

      (send-to event-access {:topic :unknown-stuff})
      (is (= {:topic :event-access/unkown-topic :payload {:topic :unknown-stuff}}
             (receive-from event-access))))))
