(ns tango.ui-db-tests
  (:require [clojure.test :refer :all]
            [tango.ui-db :as db]
            [tango.test-utils :as u]))

 ;; {:competition/classes {:db/cardinality :db.cardinality/many
 ;;                                                :db/valueType :db.type/ref}
                         





 ;;                          :round/dances  {:db/cardinality :db.cardinality/many
 ;;                                                   :db/valueType :db.type/ref}

 ;;                          ;:class/name {:db/unique :db.unique/identity}
 ;;                          }

(def schema {;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Competition
             :competition/name {:db/unique :db.unique/identity}
             
             :competition/adjudicators {:db/cardinality :db.cardinality/many
                                        :db/valueType :db.type/ref}

             :competition/panels {:db/cardinality :db.cardinality/many
                                  :db/valueType :db.type/ref}

             :competition/options {:db/isComponent true
                                   :db/valueType :db.type/ref}

             :competition/activities {:db/cardinality :db.cardinality/many
                                      :db/valueType :db.type/ref}

             :competition/classes {:db/cardinality :db.cardinality/many
                                   :db/valueType :db.type/ref}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Adjudicator Panels
             :adjudicator-panel/adjudicators {:db/cardinality :db.cardinality/many
                                              :db/valueType :db.type/ref}

             :adjudicator-panel/id {:db/unique :db.unique/identity}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Dance
             :dance/name {:db/unique :db.unique/identity}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Adjudicator
             :adjudicator/id {:db/unique :db.unique/identity}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Participant
             :participant/id {:db/unique :db.unique/identity}
             
             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Activity

             :activity/id {:db/unique :db.unique/identity}

             :activity/source {:db/cardinality :db.cardinality/one
                               :db/valueType :db.type/ref}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Rounds
             :round/id {:db/unique :db.unique/identity}
             
             :round/panel {:db/cardinality :db.cardinality/one
                           :db/valueType :db.type/ref}

             :round/dances {:db/cardinality :db.cardinality/many
                            :db/valueType :db.type/ref}

             :round/results {:db/cardinality :db.cardinality/many
                             :db/valueType :db.type/ref}

             :round/starting {:db/cardinality :db.cardinality/many
                              :db/valueType :db.type/ref}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Class
             :class/id {:db/unique :db.unique/identity}

             :class/dances {:db/cardinality :db.cardinality/many
                            :db/valueType :db.type/ref}

             :class/rounds {:db/cardinality :db.cardinality/many
                            :db/valueType :db.type/ref}

             :class/adjudicator-panel {:db/cardinality :db.cardinality/one
                                       :db/valueType :db.type/ref}

             :class/remaining {:db/cardinality :db.cardinality/many
                               :db/valueType :db.type/ref}

             :class/starting {:db/cardinality :db.cardinality/many
                              :db/valueType :db.type/ref}
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
      ;; There should be two activites
      (is (= 2
             (count (db/query conn '[:find ?acts
                                     :in $ ?competition-name
                                     :where
                                     [?e :competition/name ?competition-name]
                                     [?e :competition/activities ?acts]]
                              "TurboMegatävling"))))

      ;; a rounds panel must be a competition panel and its adjudicators
      ;;  must be a competition adjudicator
      (is (= #{{:adjudicator-panel/name "2"}
               {:adjudicator/name "Cesar"}
               {:adjudicator/name "Bertil"}}
             (into #{}
                   (flatten
                    (db/query conn '[:find
                                     (pull ?p [:adjudicator-panel/name])
                                     (pull ?adj [:adjudicator/name])
                                     :in $ ?competition-name
                                     :where
                                     [?e :competition/name ?competition-name]
                                     [?e :competition/activities ?act]
                                     [?act :activity/source ?s]
                                     [?s :round/panel ?p]
                                     [?e :competition/panels ?p]
                                     [?p :adjudicator-panel/adjudicators ?adj]
                                     [?e :competition/adjudicators ?adj]]
                              "TurboMegatävling")))))
      
      ;; round can contain dances from its class
      (is (= 0
             (db/query conn '[:find ?r
                              :in $ ?competition-name
                              :where
                              [?e :competition/name ?competition-name]
                              [?e :competition/classes ?c]
                              ;[?c :class/dances ?d]
                              [?c :class/rounds ?r]
                              ;[?r :round/dances ?d]
                              ]
                       "TurboMegatävling")))

      ;; round participants must be in the rounds class
      ;; and in the starting list
      ;; and have a result

      ;; judging adjudicators must be in round panel

      
      )))


;; :competition/classes
(deftest query-for-competition-classes
  (testing "Query to get classes"
    (let [conn (transact-small-example)]
      ;; A classes panels must be a competition panel and its adjudicators
      ;; must be a competition adjudicator
      (is (not (empty?
                (db/query conn '[:find ?adj
                                 :in $ ?competition-name
                                 :where
                                 [?e :competition/name ?competition-name]
                                 [?e :competition/classes ?c]
                                 [?e :competition/panels ?p]
                                 [?c :class/adjudicator-panel ?p]
                                 [?p :adjudicator-panel/adjudicators ?adj]
                                 [?e :competition/adjudicators ?adj]]
                          "TurboMegatävling"))))

      ;; remaining participants must be in starting list
      (is (not (empty?
                (db/query conn '[:find [?p ...]
                                 :in $ ?competition-name
                                 :where
                                 [?e :competition/name ?competition-name]
                                 [?e :competition/classes ?c]
                                 [?c :class/remaining ?p]
                                 [?c :class/starting ?p]]
                          "TurboMegatävling"))))

      ;; remaining list can not be longer that starting
      (is 
       (let [[rem-count start-count]
             (first (db/query conn '[:find (count ?r) (count ?s)
                                     :in $ ?competition-name
                                     :where
                                     [?e :competition/name ?competition-name]
                                     [?e :competition/classes ?c]
                                     [?c :class/remaining ?r]
                                     [?c :class/starting ?s]]
                              "TurboMegatävling"))]
         (>= start-count rem-count)))
      )))


;; (deftest query-for-competition-panels
;;   (testing "Query to get adjudicators panels"
;;     (let [conn (transact-small-example)]
;;       (is (= []
;;              (db/query conn '[:find ?e
;;                               :in $ ?competition-name
;;                               :where
;;                               [?e :competition/name ?competition-name]]
;;                        "TurboMegatävling"))))))






