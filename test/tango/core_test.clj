(ns tango.core-test
  (:require [clojure.test :refer :all]
            [tango.core :refer :all]
            [tango.import :as imp]
            [tango.messaging :as msg]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [clojure.core.async
             :as a
             :refer [>! <! >!! <!! chan buffer close! thread
                     alts! alts!! timeout go-loop]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import

; (clojure.test/run-tests 'tango.core-test)

;; TODO input validation of message dispatch
;; TODO test verification of broken handler map
(deftest message-dispatching
  (testing "Dispatching of client messages"
    (let [handler-map {:file-import #(hash-map :file-import-called %)}
          message-disp (msg/create-message-dispatch handler-map)]
      (is (= (message-disp {:topic :file/import :payload :the-payload :sender 1})
             {:id 1 :message [:file/imported {:content {:file-import-called :the-payload}}]}))
      (is (= (message-disp {:topic :client/ping :payload :the-payload :sender 2})
             {:id 2 :message [:server/pong []]}))
      (is (= (message-disp {:topic :no-match :payload :the-payload :sender 3})
             {:id 3 :message [:client/unkown-topic {:topic :no-match}]})))))


(deftest message-handling-loop
  (testing "Messages should be picked up from client and dispatched"
    (let [in-ch (timeout 1000)
          out-ch (timeout 1000)
          sys-ch (timeout 1000)
          dispatch-fn (fn [msg] (if (= (:test msg) :message) msg (/ 0 0)))]
      (msg/start-message-loop dispatch-fn in-ch out-ch sys-ch)
      ;; post a message to be handled
      (>!! in-ch {:test :message})
      (is (= (<!! out-ch) {:test :message}))
      ;; exceptions should be put on system channel
      (>!! in-ch {:test :exception})
      (is (= (<!! out-ch) "Exception message: Divide by zero")))))

(deftest message-sending-loop
  (testing "Messages put on the send channel should be handled by the send-to-client fn"
    (let [in-ch (timeout 1000)
          sys-ch (timeout 1000)
          called-with-ch (timeout 1000)
          send-fn (fn [msg] (if (= (:test msg) :message) (>!! called-with-ch msg) (/ 0 0)))]
      (msg/start-message-send-loop send-fn in-ch sys-ch)
      (>!! in-ch {:test :message})
      (is (= {:test :message} (<!! called-with-ch)))
      ;; exceptions should be put on system channel
      (>!! in-ch {:test :exception})
      (is (= "Exception message: Divide by zero" (<!! called-with-ch))))))


