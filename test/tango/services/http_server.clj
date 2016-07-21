(ns tango.services.http-server
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [clojure.core.async :as async]
            [com.stuartsierra.component :as component]
            [tango.http-server :as http]
            [taoensso.timbre :as log]
            [tango.datomic-storage :as ds]))

;; Turn of logging out put when running tests
(log/set-config! {:appenders {:standard-out {:enabled? false}}})

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

;; TODO
;(deftest query-handler
;  (testing "Query handler"))

(deftest http-handler-routing
  (testing "Http handler should route commands"
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
              :body {'class/delete {:result :tx/accepted}}}))))

  ;; TODO - get rid of om and datomic deps, super complected
  (testing "Http handler should route query"
    (let [out-ch (async/chan)
          out-val (async/go (first (async/alts! [out-ch (async/timeout 1000)])))
          schema-tx (read-string (slurp "./resources/schema/activity.edn"))
          _ (ds/create-storage "datomic:mem://localhost:4334//competitions" schema-tx)
          _ (ds/set-client-information (ds/create-connection "datomic:mem://localhost:4334//competitions")
                                       {:client/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                                        :client/name "Platta 3"})
          handler (http/handler nil nil {:out-channel out-ch} "datomic:mem://localhost:4334//competitions")
          result (handler {:request-method :get
                           :uri            "/query"
                           :params         {:query "[{:app/clients [:client/name]}]"}})]
      (is (= {:status 200
              :headers {}
              :body {:query {:app/clients [{:client/name "Platta 3"}]}}}
             result)))))

