(ns tango.datomic-storage-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.datomic-storage :as ds]
            [tango.import :as imp]
            [taoensso.timbre :as log]))

;; Turn of logging out put when running tests
(log/set-config! {:appenders {:standard-out {:enabled? false}}})

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



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup utils

(def mem-uri "datomic:mem://localhost:4334//competitions")

(def schema-tx (read-string (slurp "./resources/schema/activity.edn")))

(def test-competition (atom nil))
(def conn (atom nil))

(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (ds/create-storage mem-uri schema-tx)))
    (is (not= nil (ds/create-connection mem-uri)))))

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
          _ (ds/create-storage mem-uri schema-tx)
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
          _ (ds/create-storage mem-uri schema-tx)
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
          _ (ds/create-storage mem-uri  schema-tx)
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
          _ (ds/create-storage mem-uri schema-tx)
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Storage of Client information

(deftest should-be-possible-to-store-client-information
  (testing "It should be possible to store client information"
    (let [conn @conn
          init-client-tx {:client/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                          :client/name "Platta 3"}
          assoc-client-tx {:client/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                           :client/user {:adjudicator/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"}}]
      (ds/set-client-information conn init-client-tx)
      (ds/set-client-information conn assoc-client-tx)
      (is (= (ds/query-clients conn [:client/id
                                     :client/name
                                     {:client/user [:adjudicator/id]}])
             [{:client/name "Platta 3"
               :client/id   #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
               :client/user {:adjudicator/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"}}])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Management of classes

(deftest should-be-possible-to-manage-classes
  (testing "Creation of classes"
    (let [conn @conn
          competition-tx {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          class-tx {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                    :class/name "Test Class"
                    :class/dances [{:dance/name "Samba"}]
                    :class/adjudicator-panel {:adjudicator-panel/name "1"
                                              :adjudicator-panel/id #uuid "11edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}}]
      (ds/create-competition conn competition-tx)
      (ds/transact-class conn (:competition/id competition-tx) class-tx)
      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/classes [:class/id
                                                                :class/name
                                                                {:class/adjudicator-panel
                                                                 [:adjudicator-panel/id
                                                                  :adjudicator-panel/name]}
                                                                {:class/dances [:dance/name]}]}])
             [{:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name "Test Competition"
               :competition/classes [class-tx]}]))))

  (testing "Deletion of classes"
    (let [conn @conn
          competition-tx {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          class-tx-1 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class"}
          class-tx-2 {:class/id #uuid "666dcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class"}]
      (ds/create-competition conn competition-tx)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-1)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-2)
      (ds/delete-class conn
                       (:competition/id competition-tx)
                       (:class/id class-tx-1))
      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/classes [:class/id
                                                                :class/name]}])
             [{:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name "Test Competition"
               :competition/classes [class-tx-2]}]))))

  (testing "Update of a class"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          competition-tx {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          class-tx-1 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class"
                      :class/dances [{:dance/name "Samba"
                                      :dance/id #uuid "d1edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                     {:dance/name "Mango"
                                      :dance/id #uuid "d2edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]
                      :class/adjudicator-panel {:adjudicator-panel/name "1"
                                                :adjudicator-panel/id #uuid "11edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "B"
                                        :participant/id #uuid "20edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "C"
                                        :participant/id #uuid "30edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}

          class-tx-2 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class Updated"
                      :class/dances [{:dance/name "Mango"
                                      :dance/id #uuid "d2edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                     {:dance/name "Samba"
                                      :dance/id #uuid "d3edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]
                      :class/adjudicator-panel {:adjudicator-panel/name "2"
                                                :adjudicator-panel/id #uuid "12edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "D"
                                        :participant/id #uuid "40edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}]
      (ds/create-competition conn competition-tx)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-1)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-2)

      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/classes
                                          [:class/id
                                           :class/name
                                           {:class/starting [:participant/id
                                                             :participant/name]}
                                           {:class/adjudicator-panel [:adjudicator-panel/id
                                                                      :adjudicator-panel/name]}
                                           {:class/dances [:dance/name
                                                           :dance/id]}]}])
             [{:competition/id      #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name    "Test Competition"
               :competition/classes [class-tx-2]}]))))

  (testing "Update of a class should merge attributes not included"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          competition-tx {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          class-tx-1 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class"
                      :class/adjudicator-panel {:adjudicator-panel/name "1"
                                                :adjudicator-panel/id #uuid "11edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "B"
                                        :participant/id #uuid "20edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "C"
                                        :participant/id #uuid "30edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}

          class-tx-2 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class Updated"
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "D"
                                        :participant/id #uuid "40edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}]
      (ds/create-competition conn competition-tx)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-1)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-2)

      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/classes
                                          [:class/id
                                           :class/name
                                           {:class/starting [:participant/id
                                                             :participant/name]}
                                           {:class/adjudicator-panel [:adjudicator-panel/id
                                                                      :adjudicator-panel/name]}]}])
             [{:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name "Test Competition"
               :competition/classes [(assoc class-tx-2
                                       :class/adjudicator-panel
                                       {:adjudicator-panel/name "1"
                                        :adjudicator-panel/id #uuid "11edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"})]}]))))

  (testing "Updates can be done to classes that not previously had any value to the updated attribute"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          competition-tx {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}

          class-wo-all-attr-tx-1 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                                  :class/name "Test Class"}

          class-tx-2 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class Updated"
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "D"
                                        :participant/id #uuid "40edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}]
      (ds/create-competition conn competition-tx)

      (ds/transact-class conn (:competition/id competition-tx) class-wo-all-attr-tx-1)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-2)

      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/classes
                                          [:class/id
                                           :class/name
                                           {:class/starting [:participant/id
                                                             :participant/name]}]}])
             [{:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name "Test Competition"
               :competition/classes [class-tx-2]}]))))

  (testing "Create and Update can be the same operation"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          competition-tx {:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          class-tx-1 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class"
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "B"
                                        :participant/id #uuid "20edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "C"
                                        :participant/id #uuid "30edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}]
      (ds/create-competition conn competition-tx)

      (ds/transact-class conn (:competition/id competition-tx) class-tx-1)

      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/classes
                                          [:class/id
                                           :class/name
                                           {:class/starting [:participant/id
                                                             :participant/name]}]}])
             [{:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name "Test Competition"
               :competition/classes [class-tx-1]}]))))

  (testing "Order of references should not matter"
    (let [_ (ds/delete-storage mem-uri)
          _ (ds/create-storage mem-uri schema-tx)
          conn (ds/create-connection mem-uri)
          competition-tx {:competition/id   #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          class-tx-1 {:class/id       #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name     "Test Class"
                      :class/starting [{:participant/id   #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/id   #uuid "30edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/id   #uuid "20edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}

          class-tx-2 {:class/id       #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name     "Test Class Updated"
                      :class/starting [{:participant/id   #uuid "20edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/id   #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}]
      (ds/create-competition conn competition-tx)

      (ds/transact-class conn (:competition/id competition-tx) class-tx-1)
      (ds/transact-class conn (:competition/id competition-tx) class-tx-2)

      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/classes
                                          [:class/id
                                           :class/name
                                           {:class/starting [:participant/id
                                                             :participant/name]}]}])
             [{:competition/id #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name "Test Competition"
               :competition/classes [(update-in
                                       class-tx-2
                                       [:class/starting] #(vec (sort-by :participant/id %)))]}])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Management of Adjudicator Panels

(deftest adjudicator-panel-management
  (testing "Creation of panels"
    (let [conn @conn
          competition-tx {:competition/id   #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          panel-tx {:adjudicator-panel/name "New Panel"
                    :adjudicator-panel/id   #uuid "ad38da19-6fd7-41f1-a628-ef4688f2f2dc"
                    :adjudicator-panel/adjudicators
                                            [{:adjudicator/id     #uuid "dfa07b2c-d583-4ff3-aa43-5d9b49129474"
                                              :adjudicator/number 0
                                              :adjudicator/name   "AA"}
                                             {:adjudicator/id     #uuid "8b464e71-fcd2-4a7a-8c15-9a240cf750aa"
                                              :adjudicator/number 1
                                              :adjudicator/name   "BB"}]}]
      (ds/create-competition conn competition-tx)
      (ds/transact-adjudicator-panels conn (:competition/id competition-tx) panel-tx)
      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/panels [:adjudicator-panel/id
                                                               :adjudicator-panel/name
                                                               {:adjudicator-panel/adjudicators
                                                                [:adjudicator/id
                                                                 :adjudicator/name
                                                                 :adjudicator/number]}]}])
             [{:competition/id      #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name    "Test Competition"
               :competition/panels [panel-tx]}]))))
  (testing "Update existing panel"
    (let [panel-tx {:adjudicator-panel/name "New Panel"
                    :adjudicator-panel/id   #uuid "ad38da19-6fd7-41f1-a628-ef4688f2f2dc"
                    :adjudicator-panel/adjudicators
                                            [{:adjudicator/id     #uuid "dfa07b2c-d583-4ff3-aa43-5d9b49129474"
                                              :adjudicator/number 0
                                              :adjudicator/name   "AA"}
                                             {:adjudicator/id     #uuid "8b464e71-fcd2-4a7a-8c15-9a240cf750aa"
                                              :adjudicator/number 1
                                              :adjudicator/name   "BB"}]}
          panel-update-tx {:adjudicator-panel/name "New Panel Updated"
                           :adjudicator-panel/id   #uuid "ad38da19-6fd7-41f1-a628-ef4688f2f2dc"
                           :adjudicator-panel/adjudicators
                                                   [{:adjudicator/id     #uuid "dfa07b2c-d583-4ff3-aa43-5d9b49129474"
                                                     :adjudicator/number 1
                                                     :adjudicator/name   "AA"}
                                                    {:adjudicator/id     #uuid "88464e71-fcd2-4a7a-8c15-9a240cf750aa"
                                                     :adjudicator/number 2
                                                     :adjudicator/name   "CC"}]}]
      (ds/transact-adjudicator-panels @conn #uuid "1ace2915-42dc-4f58-8017-dcb79f958463" panel-tx)
      (ds/transact-adjudicator-panels @conn #uuid "1ace2915-42dc-4f58-8017-dcb79f958463" panel-update-tx)
      (is (= (ds/query-competition @conn [:competition/name
                                          :competition/id
                                         {:competition/panels [:adjudicator-panel/id
                                                               :adjudicator-panel/name
                                                               {:adjudicator-panel/adjudicators
                                                                [:adjudicator/id
                                                                 :adjudicator/name
                                                                 :adjudicator/number]}]}])
             [{:competition/id      #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name    "Test Competition"
               :competition/panels [panel-update-tx]}]))))
  (testing "Merging Update of existing panel"
    (let [panel-tx-1 {:adjudicator-panel/id   #uuid "ad38da19-6fd7-41f1-a628-ef4688f2f2dc"
                      :adjudicator-panel/adjudicators
                                              [{:adjudicator/id     #uuid "dfa07b2c-d583-4ff3-aa43-5d9b49129474"
                                                :adjudicator/number 0
                                                :adjudicator/name   "AA"}
                                               {:adjudicator/id     #uuid "8b464e71-fcd2-4a7a-8c15-9a240cf750aa"
                                                :adjudicator/number 1
                                                :adjudicator/name   "BB"}]}
          panel-tx-2 {:adjudicator-panel/name "Panel-2"
                      :adjudicator-panel/id   #uuid "a138da19-6fd7-41f1-a628-ef4688f2f2dc"
                      :adjudicator-panel/adjudicators []}
          panel-update-tx {:adjudicator-panel/name "New Panel Updated"
                           :adjudicator-panel/id   #uuid "ad38da19-6fd7-41f1-a628-ef4688f2f2dc"
                           :adjudicator-panel/adjudicators
                                                   [{:adjudicator/id     #uuid "dfa07b2c-d583-4ff3-aa43-5d9b49129474"
                                                     :adjudicator/number 1
                                                     :adjudicator/name   "AA"}]}]
      (ds/transact-adjudicator-panels @conn #uuid "1ace2915-42dc-4f58-8017-dcb79f958463" panel-tx-1)
      (ds/transact-adjudicator-panels @conn #uuid "1ace2915-42dc-4f58-8017-dcb79f958463" panel-tx-2)
      (ds/transact-adjudicator-panels @conn #uuid "1ace2915-42dc-4f58-8017-dcb79f958463" panel-update-tx)
      (is (= (ds/query-competition @conn [:competition/name
                                          :competition/id
                                          {:competition/panels [:adjudicator-panel/id
                                                                :adjudicator-panel/name
                                                                {:adjudicator-panel/adjudicators
                                                                 [:adjudicator/id
                                                                  :adjudicator/name
                                                                  :adjudicator/number]}]}])
             [{:competition/id      #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name    "Test Competition"
               :competition/panels [{:adjudicator-panel/id   #uuid "ad38da19-6fd7-41f1-a628-ef4688f2f2dc"
                                     :adjudicator-panel/name "New Panel Updated"
                                     :adjudicator-panel/adjudicators
                                                             [{:adjudicator/id     #uuid "dfa07b2c-d583-4ff3-aa43-5d9b49129474"
                                                               :adjudicator/number 1
                                                               :adjudicator/name   "AA"}]}
                                    {:adjudicator-panel/name "Panel-2"
                                     :adjudicator-panel/id   #uuid "a138da19-6fd7-41f1-a628-ef4688f2f2dc"}]}]))))

  (testing "Deletion of panels"
    (let [conn @conn
          competition-tx {:competition/id   #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
                          :competition/name "Test Competition"}
          panel-tx {:adjudicator-panel/name "New Panel"
                    :adjudicator-panel/id   #uuid "ad38da19-6fd7-41f1-a628-ef4688f2f2dc"
                    :adjudicator-panel/adjudicators
                                            [{:adjudicator/id     #uuid "dfa07b2c-d583-4ff3-aa43-5d9b49129474"
                                              :adjudicator/number 0
                                              :adjudicator/name   "AA"}
                                             {:adjudicator/id     #uuid "8b464e71-fcd2-4a7a-8c15-9a240cf750aa"
                                              :adjudicator/number 1
                                              :adjudicator/name   "BB"}]}
          panel-tx-2 {:adjudicator-panel/name "Panel-2"
                      :adjudicator-panel/id   #uuid "a138da19-6fd7-41f1-a628-ef4688f2f2dc"}]
      (ds/create-competition conn competition-tx)
      (ds/transact-adjudicator-panels conn (:competition/id competition-tx) panel-tx)
      (ds/delete-adjudicator-panel conn (:competition/id competition-tx) (:adjudicator-panel/id panel-tx))
      (ds/delete-adjudicator-panel conn (:competition/id competition-tx) (:adjudicator-panel/id panel-tx-2))
      (is (= (ds/query-competition conn [:competition/name
                                         :competition/id
                                         {:competition/panels [:adjudicator-panel/id
                                                               :adjudicator-panel/name
                                                               {:adjudicator-panel/adjudicators
                                                                [:adjudicator/id
                                                                 :adjudicator/name
                                                                 :adjudicator/number]}]}])
             [{:competition/id      #uuid "1ace2915-42dc-4f58-8017-dcb79f958463"
               :competition/name    "Test Competition"}]))))

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

(deftest utils
  (testing "Creation of update retractions"
    (let [class-tx-1 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :db/id 123
                      :class/name "Test Class"
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "B"
                                        :participant/id #uuid "20edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "C"
                                        :participant/id #uuid "30edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}

          class-tx-2 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                      :class/name "Test Class Updated"
                      :class/starting [{:participant/name "A"
                                        :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:participant/name "D"
                                        :participant/id #uuid "40edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}]
      (is (= (ds/create-update-retractions
               class-tx-1
               (merge class-tx-1 class-tx-2)
               (fn [class] (update-in class [:class/starting] #(vec (sort-by :participant/id %)))))
             [[:db/retract 123
               :class/starting [:participant/id #uuid "20edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"]]

              [:db/retract 123
               :class/starting [:participant/id #uuid "30edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"]]

              [:db/retract 123 :class/name "Test Class"]])))

    (testing "Should not retract nil values"
      (let [class-tx-1 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                        :db/id 123
                        :class/name "Test Class"
                        }

            class-tx-2 {:class/id #uuid "60edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"
                        :class/name "Test Class Updated"
                        :class/starting [{:participant/name "A"
                                          :participant/id #uuid "10edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                         {:participant/name "D"
                                          :participant/id #uuid "40edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}
            pre-fn (fn [class] (update-in class [:class/starting] #(vec (sort-by :participant/id %))))]
        (is (= (ds/create-update-retractions
                 class-tx-1
                 (merge class-tx-1 class-tx-2)
                 pre-fn)
               [[:db/retract 123 :class/name "Test Class"]]))))

    (testing "Uses pre diff function before comparing"
      (let [existing {:db/id 17592186053490,
                      :class/id #uuid "0b40cbfa-c2fc-475e-802e-b944f5fb2f33",
                      :class/name "New Class 111",
                      :class/starting [{:participant/id #uuid "02f4b2ec-4b69-497b-8dbd-6295ff71fc6f"}
                                       {:participant/id #uuid "8cba8eae-2225-4196-8b9d-3c1b0fb12881"}
                                       {:participant/id #uuid "70b01a4e-6dd5-44b5-8ed6-b6a8acab4399"}]}

            updated {:class/name     "New Class 111",
                     :class/id       #uuid "0b40cbfa-c2fc-475e-802e-b944f5fb2f33",
                     :class/starting [{:participant/id #uuid "8cba8eae-2225-4196-8b9d-3c1b0fb12881",
                                       :participant/number 15,
                                       :participant/name "Tyra Jönsson"}
                                      {:participant/id #uuid "70b01a4e-6dd5-44b5-8ed6-b6a8acab4399",
                                       :participant/number 16,
                                       :participant/name   "Vera Sundqvist"}]}]

        (is (= (ds/create-update-retractions
                 existing
                 updated
                 (fn [class] (update-in class [:class/starting] #(set (map (fn [x] (select-keys x [:participant/id])) %)))
                   ;(fn [class] (update-in class [:class/starting] #(vec (sort-by :participant/id %))))
                   ))
               [[:db/retract 17592186053490
                 :class/starting [:participant/id #uuid "02f4b2ec-4b69-497b-8dbd-6295ff71fc6f"]]]))))))