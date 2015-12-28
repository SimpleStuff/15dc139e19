(ns tango.services.broker-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.broker :as manager]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Unit tests

;; send :file/import
;; should send event-access/transact with result
;; should be able to query broker for persisted import

(defn create-test-service []
  (assoc
      (manager/create-message-broker)
    :channel-connection-channels (u/create-test-channels 500)
    :file-handler-channels (u/create-test-channels 500)))

(defn start-mock-channels []
  nil)

(deftest import-data-should-be-sent-to-import-engine
  (testing "Import use case"
    (let [client-channels (u/create-test-channels 1000)
          file-handler-channels (u/create-test-channels 1000)]
      (u/start-channel-loop-back file-handler-channels)
      
      ;; dispatch an import should send the import data to the import engine
      (manager/message-dispatch (u/create-message :file/import "import-data")
                                {:channel-connection-channels client-channels
                                 :file-handler-channels file-handler-channels})
      (is (= {:topic :file/import :payload "import-data"}
             (u/receive-from file-handler-channels))))))

(deftest imported-data-should-be-persisted
  (testing "Imported data from the import engine should be sent to event access"
    (let [event-access-channels (u/create-test-channels 1000)
          client-channels (u/create-test-channels 1000)]
      (u/start-channel-loop-back event-access-channels)

      ;; imported data should be sent to the event access for persisting
      (manager/message-dispatch (u/create-message :file/imported "imported-data-to-persist")
                                {:channel-connection-channels client-channels
                                 :event-access-channels event-access-channels})
      (is (= {:topic :event-access/transact :payload "imported-data-to-persist"}
             (u/receive-from event-access-channels))))))

;; TODO - add timeout to unknown topic
(deftest persisted-data-should-be-reported-to-client
  (testing "Persisted events should be reported to the client"
    (let [client-channels (u/create-test-channels 1000)]
      (manager/message-dispatch (u/create-message :event-access/transaction-result "tx-result")
                                {:channel-connection-channels client-channels})
      (is (= {:topic :event-manager/imported :payload "tx-result"}
             (u/receive-from client-channels))))))

;; (deftest import-of-a-file-should-yield-a-persisted-competition
;;   (testing "Importing a file, persisting it and then query for the content"
;;     (let [event-manager (component/start (create-test-service))]
;;       (start-mock-channels event-manager)
;;       (u/send-to (:channel-connection-channels event-manager) (u/create-message :file/import "<dummy-data>"))
;;       (is (= {:topic :file/imported :payload true}
;;              (u/receive-from (:channel-connection-channels event-manager)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Integration with components
