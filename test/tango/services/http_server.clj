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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Command handling
(deftest command-handler
  (testing "Command handler stuff"
    (let [out-ch (async/chan)
          out-val (async/go (first (async/alts! [out-ch (async/timeout 1000)])))
          result (http/handle-command
                   out-ch
                   '[(class/delete {:class/id 1 :competition/id 1})])]
      ;; This is the message that should be passed on
      (is (= (async/<!! out-val)
             {:topic :command :sender :http :payload (create-delete-class-message 1 1)}))
      ;; and here is the result of the http command handling
      (is (= result
             {'class/delete {:result :tx/accepted}})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Query handling

;; TODO - the Datomic ref here is CRAP, fix. Also as of now the http server do query..
(deftest query-handler
  (testing "Query handler for app/adjudicator-panels"
    (let [adjudicator-tx
          [{:adjudicator-panel/name "A"
            :adjudicator-panel/id #uuid "111e2915-42dc-4f58-8017-dcb79f958463"
            :adjudicator-panel/adjudicators
                                    [{:adjudicator/name "AA"
                                      :adjudicator/number 1
                                      :adjudicator/id #uuid "11ce2915-42dc-4f58-8017-dcb79f958463"}]}]
          schema-tx (read-string (slurp "./resources/schema/activity.edn"))
          _ (ds/create-storage "datomic:mem://localhost:4334//competitions" schema-tx)
          _ (ds/create-competition (ds/create-connection
                                   "datomic:mem://localhost:4334//competitions")
                                 {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                                  :competition/name "Test Competition"
                                  :competition/panels adjudicator-tx})
          result (http/handle-query
                   (async/timeout 100)
                   "datomic:mem://localhost:4334//competitions"
                   '[{:app/adjudicator-panels
                      [:adjudicator-panel/id :adjudicator-panel/name
                       {:adjudicator-panel/adjudicators
                        [:adjudicator/name :adjudicator/number :adjudicator/id]}]}])]
      (is (= result
             {:app/adjudicator-panels adjudicator-tx}))))

  (testing "Query handler for app/adjudicators"
    (let [adjudicator-tx
          [{:adjudicator-panel/name "A"
            :adjudicator-panel/id #uuid "111e2915-42dc-4f58-8017-dcb79f958463"
            :adjudicator-panel/adjudicators
                                    [{:adjudicator/name "AA"
                                      :adjudicator/number 1
                                      :adjudicator/id #uuid "11ce2915-42dc-4f58-8017-dcb79f958463"}]}]
          schema-tx (read-string (slurp "./resources/schema/activity.edn"))
          _ (ds/create-storage "datomic:mem://localhost:4334//competitions" schema-tx)
          _ (ds/create-competition (ds/create-connection
                                     "datomic:mem://localhost:4334//competitions")
                                   {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                                    :competition/name "Test Competition"
                                    :competition/panels adjudicator-tx})
          result (http/handle-query
                   (async/timeout 100)
                   "datomic:mem://localhost:4334//competitions"
                   '[{:app/adjudicators
                      [:adjudicator/name :adjudicator/number :adjudicator/id]}])]
      (is (= result
             {:app/adjudicators [{:adjudicator/name "AA"
                                  :adjudicator/number 1
                                  :adjudicator/id #uuid "11ce2915-42dc-4f58-8017-dcb79f958463"}]})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Http routing
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

