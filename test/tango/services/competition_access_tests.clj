(ns tango.services.competition-access-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of complete competitions

(deftest access-stored-competition
  (testing "Access a competition previously stored"
    (is (= 1 0))))
