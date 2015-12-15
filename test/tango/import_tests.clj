(ns tango.import-tests
  (:require [clojure.test :refer :all]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [tango.import :as imp]
            [tango.test-utils :as u]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

;; Generate example data
(defn generate-expected [name data]
  (spit (str "expected_" name "_result.clj.test")
        (pr-str (let [current-id (atom 0)]
                  (imp/competition-xml->map data #(swap! current-id inc))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of complete competitions

(deftest import-dance-perfect-competition
  (testing "Import competition from a Dance Perfect file"  
    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map u/small-example #(swap! current-id inc))
             u/expected-small-example)))

    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map u/real-example #(swap! current-id inc))
             u/expected-real-example)))

    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map u/real-example-kungsor #(swap! current-id inc))
             u/expected-real-example-kungsor)))

    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map u/real-example-uppsala #(swap! current-id inc))
             u/expected-real-example-uppsala)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests for different parts of a competition

(deftest import-rounds
  (testing "Import rounds"
    (let [current-id (atom 0)]
      (is (= (map :class/rounds
                  (:competition/classes
                   (imp/competition-xml->map u/small-example #(swap! current-id inc))))
             (map :class/rounds
                  (:competition/classes
                   u/expected-small-example)))))))

(deftest import-classes
  (testing "Import classes"
    (let [current-id (atom 0)]
      (is (= (:competition/classes
              (imp/competition-xml->map u/small-example #(swap! current-id inc)))
             (:competition/classes
              u/expected-small-example))))))

(deftest import-adjudicators
  (testing "Import of adjudicators"
    (let [current-id (atom 0)]
      (is (= (:competition/adjudicators
              (imp/competition-xml->map u/small-example #(swap! current-id inc)))
             (:competition/adjudicators
              u/expected-small-example))))))

(deftest import-dance-perfect-panels
  (testing "Import of adjudicator panels"
    (let [current-id (atom 0)]
      (is (= (:competition/panels
              (imp/competition-xml->map u/small-example #(swap! current-id inc)))
             (:competition/panels
             u/expected-small-example))))))

(deftest import-dance-perfect-competition-data
  (testing "Import competition data of Dance Perfect file"
    (let [current-id (atom 0)]
      (is (= (select-keys (imp/competition-xml->map u/small-example #(swap! current-id inc))
                          [:competition/name :competition/date :competition/location])
             {:competition/name "TurboMegatävling"
              :competition/date (tcr/to-date (tc/date-time 2014 11 22))
              :competition/location "THUNDERDOME"})))))

