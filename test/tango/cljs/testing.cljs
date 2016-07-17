(ns tango.cljs.testing
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [tango.cljs.runtime.read :as r]
            [tango.cljs.runtime.mutation :as m]))

(deftest first-test
  (is (= 1 1)))

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