(ns tango.cljs.testing
  (:require-macros
    [devcards.core :as dc :refer [defcard deftest]])

  (:require [cljs.test :refer-macros [is testing run-tests]]
            [tango.cljs.runtime.core :as rtc]
            [tango.cljs.runtime.read :as r]
            [tango.cljs.runtime.mutation :as m]
            [om.next :as om]))

;; github, ladderlift/om-fullstack-example
(defcard dummy
         "Dummy card")
;; POF testing namespace, break into proper ns when doing real stuff
(deftest first-test
         "Should show as card"
         (testing "Stuff"
           (is (= 1 1))))

(dc/deftest test-read
  (let [result (r/read {:state (atom {:app/clients [{:client/name "A"}]})}
                       :app/clients {})]
    (is (= [{:client/name "A"}] (:value result)))))

(dc/deftest test-mutate
  (testing "Mutation"
    (let [result (m/mutate {:state (atom {:app/selected-page :page-one})}
                           'app/select-page
                           {:selected-page :page-two})]
      (is (= {:app/selected-page :page-two}
             ((:action result)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(dc/deftest
  make-commands
  (testing "Make commands"
    (is (= (rtc/make-select-page-command :dances)
           `[(app/select-page {:selected-page :dances})
             :app/selected-page]))
    (is (= (rtc/make-select-page-command :classes)
           `[(app/select-page {:selected-page :classes})
             :app/selected-page]))))

(dc/deftest
  select-page
  (testing "Selecting page should transact the correct command."
    (is (= (rtc/select-page rtc/reconciler :classes)
           `{app/select-page
             {:keys   [:app/selected-page]
              :result ~(merge @rtc/app-state {:app/selected-page :classes})}
             :app/selected-page :classes}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Runtime reads
(dc/deftest
  adjudicators-read
  (let [result (r/read {:state
                        (atom {:app/adjudicators
                               [{:adjudicator/id   1
                                 :adjudicator/name "A"}]})}
                       :app/adjudicators
                       {})]
    (is (= [{:adjudicator/name "A"
             :adjudicator/id 1}]
           (:value result)))
    (is (= (:query result)
           true))))

(dc/deftest
  selected-adjudicator-read
  (let [result (r/read {:state
                        (atom {:app/selected-adjudicator
                               {:adjudicator/id   1
                                :adjudicator/name "A"}})}
                       :app/selected-adjudicator
                       {})]
    (is (= {:value
            {:adjudicator/name "A"
             :adjudicator/id   1}}
           result))))

(dc/deftest
  select-adjudicator-command
  (let [adjudicator {:adjudicator/id 1 :adjudicator/name "A"}
        test-state (atom {:app/selected-adjudicator {}
                          :app/adjudicators [adjudicator]})
        reconciler (om/reconciler {:state test-state
                                   :remotes [:command :query]
                                   :parser (rtc/make-parser)
                                   :send #()})]
    (is (= {'app/select-adjudicator
            {:keys   []
             :result (merge @test-state {:app/selected-adjudicator adjudicator})}
            :app/selected-adjudicator adjudicator}
           (rtc/select-adjudicator reconciler {:adjudicator/id 1})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjudicatorPanels Component
(dc/deftest
  adjudicator-panels-read
  (let [result (r/read {:state
                        (atom {:app/adjudicator-panels
                               [{:adjudicator-panel/id   1
                                 :adjudicator-panel/name "A"}]})}
                       :app/adjudicator-panels
                       {})]
    (is (= [{:adjudicator-panel/name "A"
             :adjudicator-panel/id 1}]
           (:value result)))
    (is (= (:query result)
           true))))

(dc/deftest
  selected-panel-read
  (let [result (r/read {:state
                        (atom {:app/selected-panel
                               {:adjudicator-panel/id   1
                                :adjudicator-panel/name "A"}})}
                       :app/selected-panel
                       {})]
    (is (= {:value
            {:adjudicator-panel/name "A"
             :adjudicator-panel/id   1}}
           result))))

(dc/deftest
  adjudicator-panels-view-properties
  (testing "Query should be correct"
    (is (= [:adjudicator-panel/id :adjudicator-panel/name
            {:adjudicator-panel/adjudicators [:adjudicator/name :adjudicator/number :adjudicator/id]}]
           (om/get-query rtc/AdjudicatorPanelsRow)))))

(dc/deftest create-adjudicator-panel
  (testing "Create a new adjudicator panel"
    (let [parser (rtc/make-parser)
          result (parser {:state (atom
                                   {:app/adjudicator-panels
                                    [{:adjudicator-panel/id   1
                                      :adjudicator-panel/name "A"}]})}
                         `[(panel/create {:panel/name "B" :panel/id 2})])]
      (is (= '{panel/create
               {:keys []
                :result {:app/adjudicator-panels
                         [{:adjudicator-panel/id   1
                           :adjudicator-panel/name "A"}
                          {:adjudicator-panel/id   2
                           :adjudicator-panel/name "B"}]}}}
             result)))))

(dc/deftest update-adjudicator-panel
  (testing "Update a adjudicator panel. Update applies to the selected panel and do not involve remotes"
    (let [parser (rtc/make-parser)
          result (parser {:state (atom
                                   {:app/selected-panel
                                    {:adjudicator-panel/id   1
                                     :adjudicator-panel/name "A"}})}
                         `[(panel/update {:adjudicator-panel/name "B"
                                          :adjudicator-panel/id 1
                                          :adjudicator-panel/adjudicators {:adjudicator/id 11
                                                                           :adjudicator/name "AA"}})])]
      (is (= '{panel/update
               {:keys [:app/selected-panel]
                :result {:app/selected-panel
                         {:adjudicator-panel/name "B"
                          :adjudicator-panel/id 1
                          :adjudicator-panel/adjudicators {:adjudicator/id 11
                                                           :adjudicator/name "AA"}}}}}
             result)))))

;; TODO - fix so that remotes only are included in prod. so they don't mess up tests
(dc/deftest select-adjudicator-panel
  (testing "Mark a adjduicator panel as selected"
    (let [parser (rtc/make-parser)
          result (parser {:state (atom
                                   {:app/adjudicator-panels
                                    [{:adjudicator-panel/id   1
                                      :adjudicator-panel/name "A"}]})}
                         `[(app/select-panel {:panel/id 1})])]
      (is (= '{app/select-panel
               {:keys   []
                :result {:app/adjudicator-panels
                                             [{:adjudicator-panel/id   1
                                               :adjudicator-panel/name "A"}]

                         :app/selected-panel {:adjudicator-panel/id   1
                                              :adjudicator-panel/name "A"}}}}
             result)))))

;; save
(dc/deftest save-adjudicator-panel
  (testing "Save an adjudicator panel. Save includes remote"
    (let [parser (rtc/make-parser)
          selected-panel {:adjudicator-panel/id           1
                          :adjudicator-panel/name         "B"
                          :adjudicator-panel/adjudicators [{:adjudicator/id   11
                                                            :adjudicator/name "AA"}]}
          result (parser {:state (atom
                                   {:app/adjudicator-panels
                                    [{:adjudicator-panel/id   1
                                      :adjudicator-panel/name "A"}]
                                    :app/selected-panel selected-panel})}
                         `[(adjudicator-panel/save {:panel ~selected-panel})])]
      (is (= '{adjudicator-panel/save
               {:keys   [:app/adjudicator-panels]
                :result {:app/adjudicator-panels
                         [{:adjudicator-panel/id           1
                           :adjudicator-panel/name         "B"
                           :adjudicator-panel/adjudicators [{:adjudicator/id   11
                                                             :adjudicator/name "AA"}]}]
                         :app/selected-panel
                         {:adjudicator-panel/name         "B"
                          :adjudicator-panel/id           1
                          :adjudicator-panel/adjudicators [{:adjudicator/id   11
                                                            :adjudicator/name "AA"}]}}}}
             result)))))

;; delete
(dc/deftest delete-adjudicator-panel
  (testing "Delete an adjudicator panel. Save includes remote"
    (let [parser (rtc/make-parser)

          result (parser {:state (atom
                                   {:app/adjudicator-panels
                                                        [{:adjudicator-panel/id   1
                                                          :adjudicator-panel/name "A"}]})}
                         `[(adjudicator-panel/delete {:adjudicator-panel/id 1})])]
      (is (= '{adjudicator-panel/delete
               {:keys   [:app/adjudicator-panels]
                :result {:app/adjudicator-panels
                         []
                         }}}
             result)))))

(dc/deftest make-panel-commands
  (testing "Construction of commands"
    (is (= (rtc/make-create-panel-command (fn [] 1))
           `[(app/select-page {:selected-page :create-panel})
             (panel/create {:panel/name "New Panel", :panel/id 1})
             (app/select-panel {:panel/id 1})
             :app/selected-page]))))
