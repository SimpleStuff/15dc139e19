(ns tango.services.event-file-storage-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.event-file-storage :as storage]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn create-test-service []
  (assoc
      (storage/create-event-file-storage)
    :event-file-storage-channels
    (component/start (storage/create-event-file-storage-channels))))

(defn- send-to [service message]
  (async/>!! (:in-channel (:event-file-storage-channels service)) message))

(defn- receive-from [service]
  (async/<!! (:out-channel (:event-file-storage-channels service))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of event file storage



(deftest access-stored-event
  (testing "Access a file stored event"
    (let [event-storage (component/start (create-test-service))]
      (send-to event-storage {:topic :event-file-storage/add :payload u/expected-small-example})
      (is (= {:topic :event-file-storage/added :payload nil}
             (receive-from event-storage)))

      (send-to event-storage {:topic :event-file-storage/add :payload u/expected-real-example})
      (is (= {:topic :event-file-storage/added :payload nil}
             (receive-from event-storage)))

      (send-to event-storage {:topic :event-file-storage/query :payload [:competition/name]})
      (is (= {:topic :event-file-storage/result :payload [{:competition/name "TurboMegatävling"}
                                                          {:competition/name "Rikstävling disco"}]}
             (receive-from event-storage)))
      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Service tests

(deftest instantiate-event-file-storage
  (testing "Instansiate event-file-storage service"
    (let [event-storage (storage/create-event-file-storage)
          event-storage-channels (storage/create-event-file-storage-channels)]
      (is (= tango.event_file_storage.EventFileStorage (class event-storage)))
      (is (= tango.event_file_storage.EventFileStorageChannels (class event-storage-channels))))))

(deftest service-life-cycle
  (testing "Service life cycle"
    (let [event-storage (component/start (create-test-service))
          stopped-event-storage (component/stop event-storage)]
      (is (= 2 (count (keys event-storage))))

      (is (not= nil (:event-file-storage-channels event-storage)))
      (is (not= nil (:message-handler event-storage)))

      (is (= nil (:event-file-storage-channels stopped-event-storage)))
      (is (= nil (:message-handler stopped-event-storage))))))

(deftest system-component-properties
  (testing "Service is a well behavied system service"
    (let [event-storage (component/start (create-test-service))]
      (send-to event-storage {:topic :event-file-storage/ping})
      (is (= {:topic :event-file-storage/pong}
             (receive-from event-storage)))

      (send-to event-storage {:topic :unknown-stuff})
      (is (= {:topic :event-file-storage/unkown-topic :payload {:topic :unknown-stuff}}
             (receive-from event-storage))))))
