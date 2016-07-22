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
;; Runtime reads
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjudicatorPanelsView Component
(dc/deftest
  adjudicator-panels-view-properties
  (testing "Query should be correct"
    (is (= [:adjudicator-panel/id :adjudicator-panel/name
            {:adjudicator-panel/adjudicators [:adjudicator/name :adjudicator/number]}]
           (om/get-query rtc/AdjudicatorPanelsRow)))))

(dc/deftest create-adjudicator-panel
  (testing "Create a new adjudicator panel"
    (let [parser (rtc/make-parser)
          result (parser {:state (atom
                                   {:app/adjudicator-panels
                                    [{:adjudicator-panel/id   1
                                      :adjudicator-panel/name "A"}]})}
                         `[(adjpanel/create {:panel/name "B" :panel/id 2})])]
      (is (= '{adjpanel/create
               {:keys []
                :result {:app/adjudicator-panels
                         [{:adjudicator-panel/id   1
                           :adjudicator-panel/name "A"}
                          {:adjudicator-panel/id   2
                           :adjudicator-panel/name "B"}]}}}
             result)))))

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

;; TODO - read the selected panel

(dc/deftest make-panel-commands
  (testing "Construction of commands"
    (is (= (rtc/make-create-panel-command (fn [] 1))
           `[(app/select-page {:selected-page :create-panel})
             (panel/create {:panel/name "New Class", :panel/id 1})
             (app/select-panel {:panel/id 1})
             :app/selected-page]))))