(ns tango.services.event-access-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.event-access :as ea]
            [tango.event-file-storage :as fs]
            [tango.expected.expected-small-result :as esr]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(def storage-path "./test/file-store.dat")

(defn create-test-service []
  (assoc
      (ea/create-event-access)
    :event-access-channels
    (component/start (ea/create-event-access-channels))
    :storage-channels
    {:in-channel (async/timeout 1000)
     :out-channel (async/timeout 1000)}))

(defn- create-test-service-with-file-storage []
  (assoc
      (ea/create-event-access)
    :event-access-channels
    (component/start (ea/create-event-access-channels))
    :storage-channels
    (let [storage-channels (component/start (fs/create-event-file-storage-channels))
          storage (component/start (assoc
                                       (fs/create-event-file-storage storage-path)
                                     :event-file-storage-channels storage-channels))]
      storage-channels)))

(defn- send-to [service message]
  (u/send-to (:event-access-channels service) message))

(defn- receive-from [service]
  (u/receive-from (:event-access-channels service)))

(defn- start-test-storage [service]
  (async/go
    (async/>! (:out-channel (:storage-channels service))
              (async/<! (:in-channel (:storage-channels service))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of event access

(deftest query-should-be-run-on-storage
  (testing "A query should be run on storage"
    (let [event-access (component/start (create-test-service))]
      (start-test-storage event-access)
      (send-to event-access {:topic :event-access/query :payload [:competition/name]})
      (is (= {:topic :event-access/query-result :payload [:competition/name]}
             (receive-from event-access))))))

(deftest transact-with-file-storage
  (testing "A query should be run on file storage"
    (let [event-access (component/start (create-test-service-with-file-storage))]
      (send-to event-access {:topic :event-access/transact :payload esr/expected-small-example})
      (is (= {:topic :event-access/transaction-result :payload nil}
             (receive-from event-access)))

      (send-to event-access {:topic :event-access/query :payload [[:competition/name]]})
      (is (= {:topic :event-access/query-result :payload [{:competition/name "TurboMegatävling"}]}
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
      (is (= 3 (count (keys event-access))))

      (is (not= nil (:event-access-channels event-access)))
      (is (not= nil (:message-handler event-access)))
      (is (not= nil (:storage-channels event-access)))

      (is (= nil (:event-access-channels stopped-event-access)))
      (is (= nil (:message-handler stopped-event-access)))
      (is (= nil (:storage-channels stopped-event-access))))))


(deftest system-component-properties
  (testing "Service is a well behavied system service"
    (let [event-access (component/start (create-test-service))]
      (send-to event-access {:topic :event-access/ping})
      (is (= {:topic :event-access/pong}
             (receive-from event-access)))

      (send-to event-access {:topic :unknown-stuff})
      (is (= {:topic :event-access/unkown-topic :payload {:topic :unknown-stuff}}
             (receive-from event-access))))))
