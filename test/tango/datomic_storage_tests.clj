(ns tango.datomic-storage-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.datomic-storage :as ds]))

;(defn- transact-small-example []
;  (let [conn (db/create-connection db/schema)]
;    (db/transform-competition conn (fn [] (db/sanitize u/expected-small-example)))
;    conn))
;
(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (ds/create-connection {})))))

;(deftest add-competition
;  (testing "Add map represention of a competition"
;    (let [conn (db/create-connection {})]
;      (is (= [:db-before :db-after :tx-data :tempids :tx-meta]
;             (keys (db/transform-competition conn (fn [] u/expected-small-example))))))))
;
;(deftest query-for-competition-info
;  (testing "Query to get competition info"
;    (let [conn (transact-small-example)]
;      (is (= [{:competition/name "TurboMegatävling"
;               :competition/location "THUNDERDOME"
;               :competition/date #inst "2014-11-22T00:00:00.000-00:00"}]
;             (db/query conn '[:find [(pull ?e [:competition/name
;                                               :competition/location
;                                               :competition/date])]
;                              :where [?e :competition/name]]))))))
