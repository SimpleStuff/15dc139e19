(ns tango.import-datoms-tests
  (:require [clojure.test :refer :all]
            [tango.import :as imp]
            [tango.test-utils :as u]
            [tango.expected.expected-small-result :as esr]
            [tango.datomic-storage :as ds]
            [clojure.edn :as edn]

            [schema.core :as s]))


(def mem-uri "datomic:mem://localhost:4334//competitions")

(def schema-tx (read-string (slurp "./src/tango/schema/activity.edn")))

(def adjudicator-schema
  "A schema describing an adjudicator"
  {:adjudicator/name s/Str
   :adjudicator/id s/Uuid
   :adjudicator/country s/Str
   :adjudicator/number s/Int})

(def test-competition (atom nil))
(def conn (atom nil))

(defn setup-db [test-fn]
  (ds/delete-storage mem-uri)
  (ds/create-storage mem-uri schema-tx)
  (reset! test-competition (ds/clean-import-data
                             (imp/competition-xml->map
                               u/real-example
                               #(java.util.UUID/randomUUID))))
  (reset! conn (ds/create-connection mem-uri))
  (test-fn))

(use-fixtures :each setup-db)

(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (ds/create-storage mem-uri schema-tx)))
    (is (not= nil (ds/create-connection mem-uri)))))

(deftest import-competition
  (testing "Import of complete competition"
    (let [competition-data (ds/clean-import-data
                             (imp/competition-xml->map
                               u/real-example
                               #(java.util.UUID/randomUUID)))
          _ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          _ (ds/transact-competition conn [competition-data])]
      (is (= (count (ds/query-adjudicators conn ['*]))
             6)))))

(deftest import-adjudicators
  (testing "Import of adjudicator data"
    (let [competition-data (:competition/adjudicators @test-competition)
          _ (ds/transact-competition @conn competition-data)]
      (is (seq (mapv #(s/validate adjudicator-schema (dissoc % :db/id))
                     (ds/query-adjudicators @conn ['*])))))))

(deftest import-adjudicator-panels
  (testing "Import of adjudicator panels data"
    (let [competition-data (:competition/panels @test-competition)
          _ (ds/transact-competition @conn competition-data)]
      (is (= (count (ds/query-adjudicator-panels @conn ['*]))
             4)))))

;; TODO - more tests of rounds etc
(deftest import-classes
  (testing "Import of classes data"
    (let [competition-data (:competition/classes @test-competition)
          _ (ds/transact-competition @conn competition-data)]
      (is (= (count (ds/query-classes @conn ['*]))
             48)))))

(deftest import-activities
  (testing "Import of activities data"
    (let [competition-data (:competition/activities @test-competition)
          _ (ds/transact-competition @conn competition-data)]
      (is (= (count (ds/query-activities @conn ['*]))
             140)))))

(deftest import-competition-data
  (testing "Import of competition data"
    (let [competition-data (select-keys @test-competition
                             [:competition/name
                              :competition/id
                              :competition/date
                              :competition/location
                              :competition/options])
          _ (ds/transact-competition @conn [competition-data])
          query-result (first
                         (mapv #(dissoc % :competition/id)
                               (ds/query-competition @conn ['* {:competition/options ['*]}])))]
      (is (= (:competition/date query-result)
             #inst "2015-09-26T00:00:00.000-00:00"))
      (is (= (:competition/name query-result)
             "Rikstävling disco"))
      (is (= (:competition/location query-result)
             "VÄSTERÅS"))
      (is (= (dissoc (first (:competition/options query-result)) :db/id)
             {:dance-competition/same-heat-all-dances false,
              :presentation/chinese-fonts false,
              :dance-competition/heat-text-on-adjudicator-sheet true,
              :dance-competition/name-on-number-sign false,
              :dance-competition/skip-adjudicator-letter false,
              :presentation/courier-font "Courier New",
              :dance-competition/adjudicator-order-final true,
              :dance-competition/random-order-in-heats false,
              :dance-competition/club-on-number-sign false,
              :dance-competition/adjudicator-order-other false,
              :presentation/arial-font "Arial",
              :printer/preview true,
              :printer/printer-select-paper false})))))

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




