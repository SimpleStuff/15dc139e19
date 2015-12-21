(ns tango.services.import-engine-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.files :as files]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn create-test-service [id-generator-fn]
  (assoc
      (files/create-file-handler id-generator-fn)
    :file-handler-channels
    (component/start (files/create-file-handler-channels))))

(defn create-started-engine [id-generator-fn]
  (component/start (create-test-service id-generator-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of import engine

(deftest instantiate-import-engine
  (testing "Instansiate import engine service"
    (let [current-id (atom 0)
          import-engine (files/create-file-handler #(swap! current-id inc))
          import-channels (files/create-file-handler-channels)]
      (is (= tango.files.FileHandler (class import-engine)))
      (is (= tango.files.FileHandlerChannels (class import-channels))))))

;; https://www.youtube.com/watch?v=oar-T2KovwE

;; Robert C. Martin - The Land that Scrum Forgot 
;; https://www.youtube.com/watch?v=hG4LH6P8Syk
(deftest file-import-message
  (testing "Processing of a file import message"
    (let [current-id (atom 0)
          import-engine (component/start (create-test-service #(swap! current-id inc)))]
      (async/>!! (:in-channel (:file-handler-channels import-engine))
                 {:topic :file/import :payload (slurp (str u/examples-folder "small-example.xml"))})
      (is (= {:topic :file/imported
              :payload u/expected-small-example}
             (async/<!! (:out-channel (:file-handler-channels import-engine))))))))

(deftest service-life-cycle
  (testing "Service life cycle"
    (let [current-id (atom 0)
          import-engine (component/start (create-test-service #(swap! current-id inc)))
          stopped-engine (component/stop import-engine)]
      (is (= 3 (count (keys import-engine))))

      (is (not= nil (:file-handler-channels import-engine)))
      (is (not= nil (:message-handler import-engine)))
      (is (not= nil (:id-generator-fn import-engine)))

      (is (= nil (:file-handler-channels stopped-engine)))
      (is (= nil (:message-handler stopped-engine)))
      (is (= nil (:id-generator-fn stopped-engine))))))

(deftest system-component-properties
  (testing "Service is a well behavied system service"
    (let [current-id (atom 0)
          import-engine (create-started-engine #(swap! current-id inc))]
      (async/>!! (:in-channel (:file-handler-channels import-engine)) {:topic :file/ping})
      (is (= {:topic :file/pong}
             (async/<!! (:out-channel (:file-handler-channels import-engine)))))

      (async/>!! (:in-channel (:file-handler-channels import-engine)) {:topic :unknown-stuff})
      (is (= {:topic :files/unkown-topic :payload {:topic :unknown-stuff}}
             (async/<!! (:out-channel (:file-handler-channels import-engine))))))))
