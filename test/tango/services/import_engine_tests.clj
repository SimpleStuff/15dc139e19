(ns tango.services.import-engine-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.import-engine :as import]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]))

;; TODO - tests need to be generative

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn create-test-service [id-generator-fn]
  (assoc
      (import/create-file-handler id-generator-fn)
    :file-handler-channels
    (component/start (import/create-file-handler-channels))))

(defn create-started-engine [id-generator-fn]
  (component/start (create-test-service id-generator-fn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of import engine

;; TODO - update for current
;(deftest instantiate-import-engine
;  (testing "Instansiate import engine service"
;    (let [current-id (atom 0)
;          import-engine (import/create-file-handler #(swap! current-id inc))
;          import-channels (import/create-file-handler-channels)]
;      (is (= tango.import_engine.FileHandler (class import-engine)))
;      (is (= tango.import_engine.FileHandlerChannels (class import-channels))))))

;; TODO - update for current
;(deftest service-life-cycle
;  (testing "Service life cycle"
;    (let [current-id (atom 0)
;          import-engine (component/start (create-test-service #(swap! current-id inc)))
;          stopped-engine (component/stop import-engine)]
;      (is (= 3 (count (keys import-engine))))
;
;      (is (not= nil (:file-handler-channels import-engine)))
;      (is (not= nil (:message-handler import-engine)))
;      (is (not= nil (:id-generator-fn import-engine)))
;
;      (is (= nil (:file-handler-channels stopped-engine)))
;      (is (= nil (:message-handler stopped-engine))))))

;; TODO - update for current
;(deftest system-component-properties
;  (testing "Service is a well behavied system service"
;    (let [current-id (atom 0)
;          import-engine (create-started-engine #(swap! current-id inc))]
;      (async/>!! (:in-channel (:file-handler-channels import-engine)) {:topic :file/ping})
;      (is (= {:topic :file/pong}
;             (async/<!! (:out-channel (:file-handler-channels import-engine)))))
;
;      (async/>!! (:in-channel (:file-handler-channels import-engine)) {:topic :unknown-stuff})
;      (is (= {:topic :files/unkown-topic :payload {:topic :unknown-stuff}}
;             (async/<!! (:out-channel (:file-handler-channels import-engine))))))))
