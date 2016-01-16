(ns tango.ui-db-tests
  (:require [clojure.test :refer :all]
            [tango.ui-db :as db]
            [tango.test-utils :as u]))

 ;; {:competition/classes {:db/cardinality :db.cardinality/many
 ;;                                                :db/valueType :db.type/ref}
                         

 ;;                          :competition/activities {:db/cardinality :db.cardinality/many
 ;;                                                   :db/valueType :db.type/ref}

 ;;                          :activity/source {:db/cardinality :db.cardinality/one
 ;;                                            :db/valueType :db.type/ref}

 ;;                          :round/dances  {:db/cardinality :db.cardinality/many
 ;;                                                   :db/valueType :db.type/ref}

 ;;                          ;:class/name {:db/unique :db.unique/identity}
 ;;                          }

(def schema {:competition/name {:db/unique :db.unique/identity}
             
             :competition/adjudicators {:db/cardinality :db.cardinality/many
                                        :db/valueType :db.type/ref}

             :competition/panels {:db/cardinality :db.cardinality/many
                                  :db/valueType :db.type/ref}

             :competition/options {:db/isComponent true
                                   :db/valueType :db.type/ref}

             :competition/activities {:db/cardinality :db.cardinality/many
                                      :db/valueType :db.type/ref}

             :adjudicator-panel/adjudicators {:db/cardinality :db.cardinality/many
                                              :db/valueType :db.type/ref}

             :adjudicator/id {:db/unique :db.unique/identity}
             })

(defn- transact-small-example []
  (let [conn (db/create-connection schema)]
    (db/transform-competition conn (fn [] u/expected-small-example))
    conn))

(deftest create-connection
  (testing "Create a connection to db"
    (is (not= nil (db/create-connection {})))))

(deftest add-competition
  (testing "Add map represention of a competition"
    (let [conn (db/create-connection {})]
      (is (= [:db-before :db-after :tx-data :tempids :tx-meta]
             (keys (db/transform-competition conn (fn [] u/expected-small-example))))))))

(deftest query-for-competition-info
  (testing "Query to get competition info"
    (let [conn (transact-small-example)]
      (is (= [{:competition/name "TurboMegatävling"
               :competition/location "THUNDERDOME"
               :competition/date #inst "2014-11-22T00:00:00.000-00:00"}]
             (db/query conn '[:find [(pull ?e [:competition/name
                                               :competition/location
                                               :competition/date])]
                              :where [?e :competition/name]]))))))

(deftest query-for-competition-adjudicators
  (testing "Query to get adjudicators"
    (let [conn (transact-small-example)]
      (is (= #{{:adjudicator/name "Cesar"}
               {:adjudicator/name "Bertil"}
               {:adjudicator/name "Anders"}}
             (into #{}
                   (db/query conn '[:find [(pull ?a [:adjudicator/name]) ...]
                                    :in $ ?competition-name
                                    :where
                                    [?e :competition/name ?competition-name]
                                    [?e :competition/adjudicators ?a]]
                             "TurboMegatävling")))))))

;; panels
(deftest query-for-competition-panels
  (testing "Query to get adjudicators panels with thier adjudicators.
Checks that those adjudicators are the same as in the competition."
    (let [conn (transact-small-example)
          panels-with-adjs (db/query conn '[:find
                                            (pull ?p [:db/id :adjudicator-panel/id])
                                            (pull ?a [:db/id :adjudicator/id])
                              :in $ ?competition-name
                              :where
                              [?e :competition/name ?competition-name]
                              [?e :competition/adjudicators ?a]
                              [?e :competition/panels ?p]
                              [?p :adjudicator-panel/adjudicators ?a]]
                       "TurboMegatävling")
          adjs-from-comp (db/query conn '[:find
                                          [(pull ?a [:db/id :adjudicator/id]) ...]
                                          :in $ ?competition-name
                                          :where
                                          [?e :competition/name ?competition-name]
                                          [?e :competition/adjudicators ?a]]
                                   "TurboMegatävling")]
     
      (is (= 4 (count panels-with-adjs)))
      ;; The difference between adjudicators in the panels and
      ;;  adjudicators in the competition should be empty
      (is (= #{} (clojure.set/difference
                  (into #{} (map second panels-with-adjs))
                  adjs-from-comp))))))

;; options
(deftest query-for-competition-options
  (testing "Query to get competition options"
    (let [conn (transact-small-example)]
      (is (= (:competition/options u/expected-small-example)
             (dissoc (db/query conn '[:find (pull ?o [*]) .
                                      :in $ ?competition-name
                                      :where
                                      [?e :competition/name ?competition-name]
                                      [?e :competition/options ?o]]
                               "TurboMegatävling")
                     :db/id))))))


;; :competition/activities
(deftest query-for-competition-activities
  (testing "Query to get competition activities"
    (let [conn (transact-small-example)]
      (is (= []
             (db/query conn '[:find ?acts
                              :in $ ?competition-name
                              :where
                              [?e :competition/name ?competition-name]
                              [?e :competition/activities ?acts]]
                       "TurboMegatävling"))))))


;; :competition/classes

;; (deftest query-for-competition-panels
;;   (testing "Query to get adjudicators panels"
;;     (let [conn (transact-small-example)]
;;       (is (= []
;;              (db/query conn '[:find ?e
;;                               :in $ ?competition-name
;;                               :where
;;                               [?e :competition/name ?competition-name]]
;;                        "TurboMegatävling"))))))






