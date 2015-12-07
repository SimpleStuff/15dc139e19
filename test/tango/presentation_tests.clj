(ns tango.presentation-tests
  (:require [clojure.test :refer :all]
            [tango.presentation :as p]
            [tango.test-utils :as u]))

(defn- make-presentations [competition]
  (mapv #(p/time-schedule-activity-presenter
         %
         (:competition/classes competition))
       (:competition/activities competition)))

(deftest time-schedule-presentation
  (testing "Presentation of time schedule activites"
    (is (= (make-presentations u/expected-real-example)
           u/expected-real-example-presentation))

    (is (= (make-presentations u/expected-real-example-kungsor)
           u/expected-real-example-kungsor-presentation))

    (is (= (make-presentations u/expected-real-example-uppsala)
           u/expected-real-example-uppsala-presentation))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

;; Prep expected result
(defn prep-result [example]
  (clojure.pprint/pprint
   (mapv #(p/time-schedule-activity-presenter
           %
           (:competition/classes example))
         (:competition/activities example))))
