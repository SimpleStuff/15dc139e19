(ns tango.services.http-server
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [tango.http-server :as http]
            [taoensso.timbre :as log]))

;; Turn of logging out put when running tests
(log/set-config! {:appenders {:standard-out {:enabled? true}}})

(deftest routing
  (testing "Commands should be routed to command api"
    (is (= 0 1))))

(defn create-delete-class-message [competition-id class-id]
  {:topic :event-manager/delete-class
   :payload {:competition/id competition-id
             :class/id class-id}})

(deftest api
  (testing "Api stuff"
    (let [out-ch (async/chan)
          out-val (async/go (first (async/alts! [out-ch (async/timeout 1000)])))
          _ ((:action (http/mutate {:state out-ch} 'class/delete {:competition/id 1 :class/id 1})))]
      (is (= (async/<!! out-val)
             {:topic :command :sender :http :payload (create-delete-class-message 1 1)})))))

(deftest command-handler
  (testing "Command handler stuff"
    (let [out-ch (async/chan)
          out-val (async/go (first (async/alts! [out-ch (async/timeout 1000)])))
          result (http/handle-command
                   out-ch
                   {:params {:command '[(class/delete {:class/id 1 :competition/id 1})]}})]
      (is (= (async/<!! out-val)
             {:topic :command :sender :http :payload (create-delete-class-message 1 1)}))
      (is (= result
             {:body {'class/delete {:result :tx/accepted}}})))))


