(ns tango.domain-tests
  (:require [tango.domain :as dm]
            [tango.domain.adjudicator :as adj]
            [clojure.test :refer :all]
            [clojure.spec :as s]))

(deftest create-adjudicator
  (testing "Created adjudicator should conform to spec"
    (let [adjudicator (adj/create-adjudicator (java.util.UUID/randomUUID) "Rolf")]
      (is (= (s/valid? ::adj/adjudicator adjudicator)
             true)))))

