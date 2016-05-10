(ns tango.import-datoms-tests
  (:require [clojure.test :refer :all]
            [tango.import :as imp]
            [tango.test-utils :as u]
            [tango.expected.expected-small-result :as esr]
            [tango.datomic-storage :as ds]))


(def mem-uri "datomic:mem://localhost:4334//competitions")

(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (ds/create-storage mem-uri ds/activity-schema)))
    (is (not= nil (ds/create-connection mem-uri)))))

(deftest import-datoms
  (testing "Import of competition data"
    (let [current-id (atom 0)]
      (let [competition-data (imp/competition-xml->datoms u/small-example
                                                          #(swap! current-id inc))
            _ (ds/delete-storage mem-uri)
            _ (ds/create-storage mem-uri ds/activity-schema)
            conn (ds/create-connection mem-uri)]
        (is (= (ds/create-competition-data competition-data)
               nil))))))



