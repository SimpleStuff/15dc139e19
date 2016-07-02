(ns tango.import-datoms-tests
  (:require [clojure.test :refer :all]
            [tango.import :as imp]
            [tango.test-utils :as u]
            [tango.expected.expected-small-result :as esr]
            [tango.datomic-storage :as ds]
            [clojure.edn :as edn]
            [tango.domain :as dom]
            [schema.core :as s]))


(def old-data (ds/clean-import-data (imp/competition-xml->map u/real-example #(java.util.UUID/randomUUID))))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup utils

(def mem-uri "datomic:mem://localhost:4334//competitions")

(def schema-tx (read-string (slurp "./resources/schema/activity.edn")))

(def test-competition (atom nil))
(def conn (atom nil))

;; TODO - cleaning should be part of DB code
(defn clean-test-data [test-data]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        ;; Competition should have id
        (:db/id form) (if (:db/ident form)
                        (:db/ident form)
                        (if (> (count (keys form)) 1)
                          (dissoc form :db/id)
                          form))
        :else form))
    test-data))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup fixtures

(defn setup-db [test-fn]
  (ds/delete-storage mem-uri)
  (ds/create-storage mem-uri schema-tx)
  (reset! test-competition (imp/competition-xml->map
                             u/real-example
                             #(java.util.UUID/randomUUID)))
  (reset! conn (ds/create-connection mem-uri))
  (test-fn))

(use-fixtures :each setup-db)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test pull expressions

(def class-query
  ['* {:class/adjudicator-panel
       ['* {:adjudicator-panel/adjudicators ['*]}]}
   {:class/dances ['*]}
   {:class/starting ['*]}
   {:class/rounds
    ['* {:round/status ['*]
         :round/type ['*]
         :round/panel ['* {:adjudicator-panel/adjudicators ['*]}]
         :round/dances ['* ]
         :round/starting ['*]
         :round/results ['* {:result/participant ['*]
                             :result/adjudicator ['*]}]}]}])

(def activities-query
  ['* {:activity/source
       ['* {:round/status ['*]
            :round/type ['*]
            :round/panel ['* {:adjudicator-panel/adjudicators ['*]}]
            :round/dances ['* ]
            :round/starting ['*]
            :round/results ['* {:result/participant ['*]
                                :result/adjudicator ['*]}]}]}])

(def panel-query
  ['* {:adjudicator-panel/adjudicators ['*]}])

(def participants-query
  ['*])

(def class-query
  ['* {:class/adjudicator-panel
       ['* {:adjudicator-panel/adjudicators ['*]}]}
   {:class/dances ['*]}
   {:class/starting ['*]}
   {:class/rounds
    ['* {:round/status ['*]
         :round/type ['*]
         :round/panel ['* {:adjudicator-panel/adjudicators ['*]}]
         :round/dances ['* ]
         :round/starting ['*]
         :round/results ['* {:result/participant ['*]
                             :result/adjudicator ['*]}]}]}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests

(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (ds/create-storage mem-uri schema-tx)))
    (is (not= nil (ds/create-connection mem-uri)))))

(deftest import-competition
  (testing "Import of complete competition"
    (let [competition-data @test-competition
          _ (ds/transact-competition @conn competition-data)]
      (is (= (count (ds/query-adjudicators @conn ['*]))
             6))

      (is (seq (s/validate dom/competition-data-schema
                           (clean-test-data (first (ds/query-competition
                                                     @conn
                                                     ['* {:competition/options ['*]
                                                          :competition/adjudicators ['*]
                                                          :competition/activities activities-query
                                                          :competition/classes class-query
                                                          :competition/panels panel-query}])))))))))

(deftest import-adjudicators
  (testing "Import of adjudicator data"
    (let [_ (ds/transact-competition @conn @test-competition)]
      (is (seq (mapv #(s/validate dom/adjudicator %)
                     (clean-test-data (ds/query-adjudicators @conn ['*]))))))))

(deftest import-adjudicator-panels
  (testing "Import of adjudicator panels data"
    (let [_ (ds/transact-competition @conn @test-competition)]
      (is (seq (mapv #(s/validate dom/adjudicator-panel %)
                     (clean-test-data
                       (ds/query-adjudicator-panels @conn panel-query))))))))

(deftest import-classes
  (testing "Import of classes data"
    (let [_ (ds/transact-competition @conn @test-competition)]
      (is (= (count (ds/query-classes @conn ['*]))
             48))

      (is (seq (mapv #(s/validate dom/class-schema %)
                     (clean-test-data
                       (ds/query-classes @conn class-query))))))))

(deftest import-activities
  (testing "Import of activities data"
    (let [_ (ds/transact-competition @conn @test-competition)]
      (is (= (count (ds/query-activities @conn ['*]))
             140))

      (is (seq (mapv #(s/validate dom/activity-schema %)
                     (clean-test-data
                       (ds/query-activities @conn activities-query))))))))

(deftest import-participants
  (testing "Import of participants data"
    (let [_ (ds/transact-competition @conn @test-competition)]
      (is (= (count (map :participant/id (ds/query-participants @conn ['*])))
             882))

      (is (seq (mapv #(s/validate dom/participant %)
                     (clean-test-data
                       (ds/query-participants @conn class-query))))))))

(deftest import-competition-data
  (testing "Import of competition data"
    (let [competition-data (select-keys @test-competition
                             [:competition/name
                              :competition/id
                              :competition/date
                              :competition/location
                              :competition/options])
          _ (ds/transact-competition @conn competition-data)
          query-result (first (ds/query-competition @conn ['* {:competition/options ['*]}]))]
      (is (= (:competition/date query-result)
             #inst "2015-09-26T00:00:00.000-00:00"))
      (is (= (:competition/name query-result)
             "Rikstävling disco"))
      (is (= (:competition/location query-result)
             "VÄSTERÅS"))
      (is (= (dissoc (:competition/options query-result) :db/id)
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
              :printer/printer-select-paper false}))

      (is (seq (mapv #(s/validate dom/competition-data-schema %)
                     (clean-test-data
                       (ds/query-competition @conn ['* {:competition/options ['*]}]))))))))

(deftest import-real-example-kongsor
  (testing "Import of real example from Kungsör"
    (let [competition-data (imp/competition-xml->map
                             u/real-example-kungsor
                             #(java.util.UUID/randomUUID))
          _ (ds/transact-competition @conn competition-data)]
      (is (= (count (ds/query-adjudicators @conn ['*]))
             6))

      (is (seq (s/validate dom/competition-data-schema
                           (clean-test-data (first (ds/query-competition
                                                     @conn
                                                     ['* {:competition/options ['*]
                                                          :competition/adjudicators ['*]
                                                          :competition/activities activities-query
                                                          :competition/classes class-query
                                                          :competition/panels panel-query
                                                          :competition/participants participants-query}])))))))))

;; TODO - add support for this competition
;; TODO - it seems that rounds with several dances have multiple entries for marks
;; i.e. 3 adjs and rounds is 4 dances will give 12 mark entries
;(deftest import-real-example-uppsala
;  (testing "Import of real example from Uppsala"
;    (let [competition-data (imp/competition-xml->map
;                             u/real-example-uppsala
;                             #(java.util.UUID/randomUUID))
;          _ (ds/transact-competition @conn competition-data)]
;      (is (= (count (ds/query-adjudicators @conn ['*]))
;             6))
;
;      (is (seq (s/validate dom/competition-data-schema
;                           (clean-test-data (first (ds/query-competition
;                                                     @conn
;                                                     ['* {:competition/options ['*]
;                                                          :competition/adjudicators ['*]
;                                                          :competition/activities activities-query
;                                                          :competition/classes class-query
;                                                          :competition/panels panel-query}])))))))))

(deftest transform-old-result
  (testing "Transform old result format to new"
    (let [old-result
          {:result/participant-number 11,
           :result/recalled           :r,
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





