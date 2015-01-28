(ns tango.core-test
  (:require [clojure.test :refer :all]
            [tango.core :refer :all]
            [tango.import :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! go chan buffer close! thread
                     alts! alts!! timeout go-loop]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import

; (clojure.test/run-tests 'tango.core-test)

(deftest message-dispatching
  (testing "Dispatching of client messages"
    (let [handler-map {:file-import #(hash-map :file-import-called %)}
          message-disp (create-message-dispatch handler-map)]
      (is (= (message-disp {:topic :file/import :payload :the-payload :sender 1})
             {:id 1 :message [:file/imported {:content {:file-import-called :the-payload}}]}))
      (is (= (message-disp {:topic :client/ping :payload :the-payload :sender 2})
             {:id 2 :message [:server/pong []]}))
      (is (= (message-disp {:topic :no-match :payload :the-payload :sender 3})
             {:id 3 :message [:client/unkown-topic {:topic :no-match}]})))))

;; TODO test verification of broken handler map

(deftest message-handling-loop
  (testing "Messages should be picked up from client, processed and replied to"
    (is = 1 0)))


