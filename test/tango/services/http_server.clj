(ns tango.services.http-server
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [tango.http-server :as http]
            [taoensso.timbre :as log]))

;; Turn of logging out put when running tests
(log/set-config! {:appenders {:standard-out {:enabled? true}}})

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
                   '[(class/delete {:class/id 1 :competition/id 1})])]
      (is (= (async/<!! out-val)
             {:topic :command :sender :http :payload (create-delete-class-message 1 1)}))
      (is (= result
             {'class/delete {:result :tx/accepted}})))))

(deftest http-handler
  (testing "Http handler stuff"
    (let [out-ch (async/chan)
          out-val (async/go (first (async/alts! [out-ch (async/timeout 1000)])))
          handler (http/handler nil nil {:out-channel out-ch} "")
          result (handler {:request-method :post
                           :uri "/commands"
                           :params {:command '[(class/delete {:class/id 1 :competition/id 1})]}})]
      (is (= (async/<!! out-val)
             {:topic :command :sender :http :payload (create-delete-class-message 1 1)}))
      (is (= result
             {:status 200
              :headers {}
              :body {'class/delete {:result :tx/accepted}}})))))

