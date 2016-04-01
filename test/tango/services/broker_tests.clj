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


(deftest import-data-should-be-sent-to-import-engine
  (testing "Import use case"
    (let [client-channels (u/create-test-channels 1000)
          file-handler-channels (u/create-test-channels 1000)
          event-access-channels (u/create-test-channels 1000)
          rules-engine-channels (u/create-test-channels 1000)]
      (u/start-channel-loop-back file-handler-channels)
      
      ;; dispatch an import should send the import data to the import engine
      (manager/message-dispatch (u/create-message :file/import "import-data")
                                {:channel-connection-channels client-channels
                                 :file-handler-channels file-handler-channels
                                 :event-access-channels event-access-channels
                                 :rules-engine-channels rules-engine-channels})
      (is (= {:topic :file/import :payload "import-data"}
             (u/receive-from file-handler-channels))))))

(deftest imported-data-should-be-persisted
  (testing "Imported data from the import engine should be sent to event access"
    (let [client-channels (u/create-test-channels 1000)
          file-handler-channels (u/create-test-channels 1000)
          event-access-channels (u/create-test-channels 1000)
          rules-engine-channels (u/create-test-channels 1000)]
      (u/start-channel-loop-back event-access-channels)

      ;; imported data should be sent to the event access for persisting
      (manager/message-dispatch (u/create-message :file/imported "imported-data-to-persist")
                                {:channel-connection-channels client-channels
                                 :file-handler-channels file-handler-channels
                                 :event-access-channels event-access-channels
                                 :rules-engine-channels rules-engine-channels})
      (is (= {:topic :event-access/transact :payload "imported-data-to-persist"}
             (u/receive-from event-access-channels))))))

(deftest persisted-data-should-be-reported-to-client
  (testing "Persisted events should be reported to the client"
    (let [client-channels (u/create-test-channels 1000)
          file-handler-channels (u/create-test-channels 1000)
          event-access-channels (u/create-test-channels 1000)
          rules-engine-channels (u/create-test-channels 1000)]
      (u/start-channel-loop-back client-channels)
      (manager/message-dispatch (u/create-message :event-access/transaction-result "tx-result")
                                {:channel-connection-channels client-channels
                                 :file-handler-channels file-handler-channels
                                 :event-access-channels event-access-channels
                                 :rules-engine-channels rules-engine-channels})
      
      (is (= {:topic :event-manager/transaction-result :payload "tx-result"}
             (u/receive-from client-channels))))))

(deftest client-query-should-be-sent-to-access
  (testing "Query from the client should be sent to Event Access service"
    (let [client-channels (u/create-test-channels 1000)
          file-handler-channels (u/create-test-channels 1000)
          event-access-channels (u/create-test-channels 1000)
          rules-engine-channels (u/create-test-channels 1000)]
      (u/start-channel-loop-back event-access-channels)
      (manager/message-dispatch (u/create-message :event-manager/query "awsome query")
                                {:channel-connection-channels client-channels
                                 :file-handler-channels file-handler-channels
                                 :event-access-channels event-access-channels
                                 :rules-engine-channels rules-engine-channels})
      (is (= {:topic :event-access/query :payload "awsome query"}
             (u/receive-from event-access-channels))))))

(deftest query-result-should-be-sent-to-client
  (testing "Query result should be sent to the client"
    (let [client-channels (u/create-test-channels 1000)
          file-handler-channels (u/create-test-channels 1000)
          event-access-channels (u/create-test-channels 1000)
          rules-engine-channels (u/create-test-channels 1000)]
      (u/start-channel-loop-back client-channels)
      (manager/message-dispatch (u/create-message :event-access/query-result "awsome query result")
                                {:channel-connection-channels client-channels
                                 :file-handler-channels file-handler-channels
                                 :event-access-channels event-access-channels
                                 :rules-engine-channels rules-engine-channels})
      (is (= {:topic :event-manager/query-result :payload "awsome query result"}
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
