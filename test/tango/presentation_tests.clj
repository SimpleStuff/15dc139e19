(ns tango.presentation-tests
  (:require [clojure.test :refer :all]
            [tango.presentation :as p]
            [tango.test-utils :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

(defn- make-time-schedule-presentations [competition]
  (mapv (fn [act] (p/make-time-schedule-activity-presenter
                    act
                    (first
                      (filter
                        (fn [c]
                          (= (:class/id c) (:round/class-id (:activity/source act))))
                        (:competition/classes competition)))))
        (:competition/activities competition)))

(defn- make-class-presentations [example]
  (mapv p/make-class-presenter (:competition/classes example)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Time schedule presentation

;; TODO - Think about how to get good tests here..

;(deftest make-time-schedule-presentation
;  (testing "Presentation of time schedule activites"
;    (is (= (make-time-schedule-presentations u/expected-real-example)
;           u/expected-real-example-activity-presentation))
;
;    (is (= (make-time-schedule-presentations u/expected-real-example-kungsor)
;           u/expected-real-example-kungsor-activity-presentation))
;
;    (is (= (make-time-schedule-presentations u/expected-real-example-uppsala)
;           u/expected-real-example-uppsala-activity-presentation))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Class presentation

(deftest make-class-presentation
  (testing "Presentation of classes"
    (is (= (make-class-presentations u/expected-real-example)
           u/expected-real-example-classes-presentation))

    (is (= (make-class-presentations u/expected-real-example-kungsor)
           u/expected-real-example-kungsor-classes-presentation))

    (is (= (make-class-presentations u/expected-real-example-uppsala)
           u/expected-real-example-uppsala-classes-presentation))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test data utils

(defn prep-result [example]
  (clojure.pprint/pprint
   (mapv #(p/make-time-schedule-activity-presenter
           %
           (:competition/classes example))
         (:competition/activities example))))

(defn prep-class-result [example]
  (clojure.pprint/pprint
   (mapv p/make-class-presenter (:competition/classes example))))
