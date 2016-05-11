(ns tango.import-datoms-tests
  (:require [clojure.test :refer :all]
            [tango.import :as imp]
            [tango.test-utils :as u]
            [tango.expected.expected-small-result :as esr]
            [tango.datomic-storage :as ds]
            [clojure.edn :as edn]))


(def mem-uri "datomic:mem://localhost:4334//competitions")

(def schema-tx (read-string (slurp "./src/tango/schema/activity.edn")))

(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (ds/create-storage mem-uri schema-tx)))
    (is (not= nil (ds/create-connection mem-uri)))))

(deftest import-adjudicators
  (testing "Import of adjudicator data"
    (let [competition-data (imp/competition-xml->datoms
                             u/real-example
                             #(java.util.UUID/randomUUID))
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn competition-data)]
      (is (= (ds/query-adjudicators conn ['*])
             nil)))))

;(deftest import-datoms
;  (testing "Import of competition data"
;    (let [current-id (atom 0)]
;      (let [competition-data (imp/competition-xml->datoms u/small-example
;                                                          #(swap! current-id inc))
;            _ (ds/delete-storage mem-uri)
;            _ (ds/create-storage mem-uri ds/activity-schema)
;            conn (ds/create-connection mem-uri)]
;        (is (= (ds/create-competition-data competition-data)
;               nil))))))



