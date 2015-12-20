(ns tango.services.import-engine-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.files :as files]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn create-test-service []
  (assoc
      (files/create-file-handler)
    :file-handler-channels
    (component/start (files/create-file-handler-channels))))

(defn create-started-engine []
  (component/start (create-test-service)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of import engine

(deftest instantiate-import-engine
  (testing "Instansiate import engine service"
    (let [import-engine (files/create-file-handler)
          import-channels (files/create-file-handler-channels)]
      (is (= tango.files.FileHandler (class import-engine)))
      (is (= tango.files.FileHandlerChannels (class import-channels))))))

(deftest service-life-cycle
  (testing "Service life cycle"
    (let [import-engine (component/start (create-test-service))
          stopped-engine (component/stop import-engine)]
      (is (= 2 (count (keys import-engine))))

      (is (not= nil (:file-handler-channels import-engine)))
      (is (not= nil (:message-handler import-engine)))      

      (is (= nil (:file-handler-channels stopped-engine)))
      (is (= nil (:message-handler stopped-engine))))))

(deftest system-component-properties
  (testing "Service is a well behavied system service"
    (let [import-engine (create-started-engine)]
      (async/>!! (:in-channel (:file-handler-channels import-engine)) {:topic :file/ping})
      (is (= {:topic :file/pong}
             (async/<!! (:out-channel (:file-handler-channels import-engine))))))))
