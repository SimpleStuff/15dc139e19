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

;; TODO - more tests of rounds etc
(deftest import-classes
  (testing "Import of classes data"
    (let [competition-data (:competition/classes
                             (ds/clean-import-data
                               (imp/competition-xml->map
                                 u/real-example
                                 #(java.util.UUID/randomUUID))))
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn competition-data)]
      (is (= (count (ds/query-classes conn ['*]))
             48)))))

(deftest import-activities
  (testing "Import of activities data"
    (let [competition-data (:competition/activities
                             (ds/clean-import-data
                               (imp/competition-xml->map
                                 u/real-example
                                 #(java.util.UUID/randomUUID))))
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn competition-data)]
      (is (= (count (ds/query-activities conn ['*]))
             140)))))

(deftest import-competition-data
  (testing "Import of competition data"
    (let [competition-data (select-keys
                             (ds/clean-import-data
                               (imp/competition-xml->map
                                 u/real-example
                                 #(java.util.UUID/randomUUID)))
                             [:competition/name
                              ;:competition/date
                              ;:competition/location
                              ;:competition/options
                              ])
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn competition-data)]
      (is (= (count (ds/query-competition conn ['*]))
             140)))))

;:competition/name name
;:competition/date date
;:competition/location location
;:competition/options options

(deftest transform-old-result
  (testing "Transform old result format to new"
    (let [old-result
          {:result/participant-number 11,
           :result/recalled           "",
           :result/judgings [{:judging/adjudicator #uuid"c1574c96-17e5-4ad8-a40b-66d8f8f7124b",
                              :judging/marks        [{:mark/x false}]}
                             {:judging/adjudicator #uuid"f78a26da-a254-4927-b3a1-ce7a4bb8da40",
                              :judging/marks        [{:mark/x true}]}
                             {:judging/adjudicator #uuid"62490165-ee33-4bc4-a669-4b7dfb42654b",
                              :judging/marks        [{:mark/x false}]}]}]
      (is (= (mapv #(dissoc % :result/id) (ds/transform-result old-result {11 1337}))
             [{:result/mark-x      false
               :result/point       0
               :result/participant {:participant/id 1337}
               :result/adjudicator {:adjudicator/id #uuid"c1574c96-17e5-4ad8-a40b-66d8f8f7124b"}}
              {:result/mark-x      true
               :result/point       0
               :result/participant {:participant/id 1337}
               :result/adjudicator {:adjudicator/id #uuid"f78a26da-a254-4927-b3a1-ce7a4bb8da40"}}
              {:result/mark-x      false
               :result/point       0
               :result/participant {:participant/id 1337}
               :result/adjudicator {:adjudicator/id #uuid"62490165-ee33-4bc4-a669-4b7dfb42654b"}}])))))


(def old-data (ds/clean-import-data (imp/competition-xml->map u/real-example #(java.util.UUID/randomUUID))))

(select-keys old-data [:competition/name
                       ;:competition/date
                       ;:competition/location
                       ; :competition/options
                       ])



