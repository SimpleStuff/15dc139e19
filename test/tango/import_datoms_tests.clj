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

;; TODO - validate on schema
;; TODO - testa att anv'nda sanitize from ui db
(deftest import-adjudicators
  (testing "Import of adjudicator data"
    (let [competition-data (:competition/adjudicators
                             (imp/competition-xml->datoms
                               u/real-example
                               #(java.util.UUID/randomUUID)))
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn competition-data)]
      (is (= (count (ds/query-adjudicators conn ['*]))
             6)))))

(deftest import-adjudicator-panels
  (testing "Import of adjudicator panels data"
    (let [competition-data (:competition/panels
                             (imp/competition-xml->map
                               u/real-example
                               #(java.util.UUID/randomUUID)))
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn competition-data)]
      (is (= (count (ds/query-adjudicator-panels conn ['*]))
             4)))))

(deftest import-classes
  (testing "Import of classes data"
    (let [competition-data (:competition/classes
                             (imp/competition-xml->map
                               u/real-example
                               #(java.util.UUID/randomUUID)))
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn competition-data)]
      (is (= (ds/query-classes conn ['*])
             4)))))

(def old-data (imp/competition-xml->map u/real-example #(java.util.UUID/randomUUID)))

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



