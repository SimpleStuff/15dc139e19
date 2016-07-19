(ns tango.cljs.testing
  (:require-macros
    [devcards.core :as dc :refer [defcard deftest]])

  (:require [cljs.test :refer-macros [is testing run-tests]]
            [tango.cljs.runtime.read :as r]
            [tango.cljs.runtime.mutation :as m]))

(defcard dummy
         "Dummy card")
;; POF testing namespace, break into proper ns when doing real stuff
(deftest first-test
         "Should show as card"
         (testing "Stuff"
           (is (= 1 1))))

(deftest test-read
  (let [result (r/read {:state (atom {:app/clients [{:client/name "A"}]})}
                       :app/clients {})]
    (is (= [{:client/name "A"}] (:value result)))))

(deftest test-mutate
  (testing "Mutation"
    (let [result (m/mutate {:state (atom {:app/selected-page :page-one})}
                           'app/select-page
                           {:selected-page :page-two})]
      (is (= {:app/selected-page :page-two}
             ((:action result)))))))