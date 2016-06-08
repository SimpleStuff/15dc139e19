(ns tango.datomic-storage-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.datomic-storage :as ds]
            [tango.import :as imp]))

;(defn- transact-small-example []
;  (let [conn (db/create-connection db/schema)]
;    (db/transform-competition conn (fn [] (db/sanitize u/expected-small-example)))
;    conn))
;

(def select-round-data
  {:activity/id   #uuid "4b0b1db9-6e5d-4aa6-9947-cb214a4d89df"
   :activity/name "Hiphop Par Brons J1"
   :round/recall  12
   :round/name    "Normal"
   :round/heats   3
   :round/starting
                  [{:participant/number 143,
                    :participant/id #uuid "6eee044a-a9e5-4c3b-8a3b-583f566ca3b8"}
                   {:participant/number 146,
                    :participant/id #uuid "967d051d-ea8b-43d4-9dba-35fb51aedda9"}]})

(defn create-selected-round [name]
  (merge select-round-data {:activity/name name :activity/id (java.util.UUID/randomUUID)}))

;(fix-id select-round-data)

(def mem-uri "datomic:mem://localhost:4334//competitions")
(def schema-tx (read-string (slurp "./src/tango/schema/activity.edn")))

(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (ds/create-storage mem-uri ds/select-activity-schema)))
    (is (not= nil (ds/create-connection mem-uri)))))

;(deftest select-round
;  (testing "Transaction of selecting a round"
;    (let [_ (ds/delete-storage mem-uri)
;          _ (ds/create-storage mem-uri (into ds/select-activity-schema ds/application-schema))
;          conn (ds/create-connection mem-uri)]
;      (is (= [:db-before :db-after :tx-data :tempids]
;             (keys (ds/select-round conn select-round-data)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup utils

(def mem-uri "datomic:mem://localhost:4334//competitions")

(def schema-tx (read-string (slurp "./src/tango/schema/activity.edn")))

(def test-competition (atom nil))
(def conn (atom nil))

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

(deftest select-round
  (testing "Transaction of selecting a round"
    (let [competition-data @test-competition
          _ (ds/transact-competition @conn competition-data)
          acts (ds/query-activities @conn ['*])
          activity (first (filter #(= (:activity/number %) "15A") acts))]
      (is (= [:db-before :db-after :tx-data :tempids]
             (keys (ds/select-round @conn (:activity/id activity)))))

      (is (= (ds/get-selected-activities @conn ['*])
             (conj [] (assoc (dissoc activity :db/id) :activity/source #{})))))))

(deftest application-should-be-able-to-run-multiple-rounds
  (testing "The application should be able to run multiple rounds at once"
    (let [competition-data @test-competition
          _ (ds/transact-competition @conn competition-data)
          acts (ds/query-activities @conn ['*])
          activity-one (first (filter #(= (:activity/number %) "15A") acts))
          activity-two (first (filter #(= (:activity/number %) "15B") acts))
          _ (ds/select-round @conn (:activity/id activity-one))
          _ (ds/select-round @conn (:activity/id activity-two))]
      (is (= (mapv #(select-keys % [:activity/name :db/id]) (ds/get-selected-activities @conn ['*]))
             [{:activity/name "Disco Singel Guld J2"}
              {:activity/name "Disco Singel Guld J1"}])))))

(deftest application-should-be-able-to-deselect-rounds
  (testing "It should be possible to deselect a selected round"
    (let [competition-data @test-competition
          _ (ds/transact-competition @conn competition-data)
          acts (ds/query-activities @conn ['*])
          activity-one (first (filter #(= (:activity/number %) "15A") acts))
          activity-two (first (filter #(= (:activity/number %) "15B") acts))
          _ (ds/select-round @conn (:activity/id activity-one))
          _ (ds/select-round @conn (:activity/id activity-two))
          ]
      (is (= (count (ds/get-selected-activities @conn ['*])) 2))

      ;; selecting the same round twice should change nothing
      (ds/select-round @conn (:activity/id activity-one))
      (is (= (count (ds/get-selected-activities @conn ['*])) 2))

      (is (= (into #{} (ds/get-selected-activities @conn ['*]))
             (into #{} (map #(dissoc (merge % {:activity/source #{}}) :db/id)
                            [activity-one activity-two]))))

      (ds/deselect-round @conn (:activity/id activity-one))
      (is (= (ds/get-selected-activities @conn ['*]) [activity-two]))

      (ds/deselect-round @conn (:activity/id activity-two))
      (is (= (ds/get-selected-activities @conn ['*]) []))

      ;; the activites should still be present
      (is (= (first (filter #(= (:activity/number %) "15A") (ds/query-activities @conn ['*])))
             activity-one))
      (is (= (first (filter #(= (:activity/number %) "15B") (ds/query-activities @conn ['*])))
             activity-two)))))

(deftest application-should-be-able-to-run-speaker-rounds
  (testing "The application should be able to run multiple speaker rounds at once"
    (let [competition-data @test-competition
          _ (ds/transact-competition @conn competition-data)
          acts (ds/query-activities @conn ['*])
          activity-one (first (filter #(= (:activity/number %) "15A") acts))
          activity-two (first (filter #(= (:activity/number %) "15B") acts))
          _ (ds/select-speaker-round @conn (:activity/id activity-one))
          _ (ds/select-speaker-round @conn (:activity/id activity-two))]

      (is (= (into #{} (ds/get-speaker-activities @conn ['*]))
             (into #{} (map #(dissoc (merge % {:activity/source #{}}) :db/id)
                            [activity-one activity-two]))))

      (ds/deselect-speaker-round @conn (:activity/id activity-one))
      (is (= (ds/get-speaker-activities @conn ['*]) [activity-two]))

      (ds/deselect-speaker-round @conn (:activity/id activity-two))
      (is (= (ds/get-speaker-activities @conn ['*]) [])))))

(deftest adjudicator-results-can-be-transacted
  (testing "Adjudicator result can be transacted to db"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri (into ds/select-activity-schema ds/result-schema))
          conn (ds/create-connection mem-uri)
          result {:result/mark-x true
                  :result/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                  :result/participant {:participant/id #uuid "4932976a-7009-41fb-9dab-f003b89dba41"}
                  :result/adjudicator {:adjudicator/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"}
                  :result/activity {:activity/id #uuid "33501fc6-087a-47f6-b003-4edb694655e5"}}]
      (ds/set-results conn [result])
      (is (= result (first (ds/query-results conn [:result/id
                                                   :result/mark-x
                                                   {:result/adjudicator [:adjudicator/id]
                                                    :result/participant [:participant/id]
                                                    :result/activity    [:activity/id]}]
                                             (:activity/id (:result/activity result)))))))))

(deftest result-confirmations-should-be-stored
  (testing "Adjudicator result confirmation should be stored"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri (into ds/select-activity-schema ds/result-schema))
          conn (ds/create-connection mem-uri)
          confirmation {:activity/id           #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                        :activity/confirmed-by [{:adjudicator/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"}
                                                {:adjudicator/id #uuid "2ace2915-42dc-4f58-8017-dcb79f958463"}]}]
      (ds/confirm-activity conn [confirmation])
      (is (= confirmation (first (ds/query-confirmation
                                   conn [:activity/id
                                         {:activity/confirmed-by [:adjudicator/id]}])))))))

(deftest adjudicator-results-should-filter-on-given-activity
  (testing "Adjudicator result can be transacted to db"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri (into ds/select-activity-schema ds/result-schema))
          conn (ds/create-connection mem-uri)
          result-1 {:result/mark-x true
                    :result/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                    :result/participant {:participant/id #uuid "4932976a-7009-41fb-9dab-f003b89dba41"}
                    :result/adjudicator {:adjudicator/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"}
                    :result/activity {:activity/id #uuid "33501fc6-087a-47f6-b003-4edb694655e5"}}
          result-2 {:result/mark-x false
                    :result/id #uuid "666dcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                    :result/participant {:participant/id #uuid "4932976a-7009-41fb-9dab-f003b89dba41"}
                    :result/adjudicator {:adjudicator/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"}
                    :result/activity {:activity/id #uuid "66601fc6-087a-47f6-b003-4edb694655e5"}}]
      (ds/set-results conn [result-1 result-2])
      (is (= result-2 (first (ds/query-results conn [:result/id
                                                     :result/mark-x
                                                     {:result/adjudicator [:adjudicator/id]
                                                      :result/participant [:participant/id]
                                                      :result/activity    [:activity/id]}]
                                               (:activity/id (:result/activity result-2)))))))))

(deftest results-can-contain-points
  (testing "Adjudicator results can contain a point value"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri (into ds/select-activity-schema ds/result-schema))
          conn (ds/create-connection mem-uri)
          result {:result/mark-x true
                  :result/point 34
                  :result/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                  :result/participant {:participant/id #uuid "4932976a-7009-41fb-9dab-f003b89dba41"}
                  :result/adjudicator {:adjudicator/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"}
                  :result/activity {:activity/id #uuid "33501fc6-087a-47f6-b003-4edb694655e5"}}]
      (ds/set-results conn [result])
      (is (= result (first (ds/query-results conn [:result/id
                                                   :result/mark-x
                                                   :result/point
                                                   {:result/adjudicator [:adjudicator/id]
                                                    :result/participant [:participant/id]
                                                    :result/activity    [:activity/id]}]
                                             (:activity/id (:result/activity result)))))))))



;(deftest select-round-should-be-sanitized
;  (testing "Nil values etc should be removed before transacted"
;    (is (= 1 0))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;(deftest add-competition
;  (testing "Add map represention of a competition"
;    (let [conn (ds/create-connection mem-uri)]
;      (is (= [:db-before :db-after :tx-data :tempids :tx-meta]
;             (keys (ds/transform-competition
;                     conn
;                     (fn [] (select-keys u/expected-small-example
;                                         [:competition/name])))))))))


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
