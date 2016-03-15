(ns tango.import-tests
  (:require [clojure.test :refer :all]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [tango.import :as imp]
            [tango.expected.expected-small-result :as esr]
            [tango.test-utils :as u]))

;; TODO - Tests need to be refactored to some kind of generative testing, as of right now they are to combersome

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

;; Generate example data
(defn generate-expected [name data]
  (spit (str "expected_" name "_result.clj.test")
        (pr-str (let [current-id (atom 0)]
                  (imp/competition-xml->map data #(swap! current-id inc))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of complete competitions

;(deftest import-dance-perfect-competition-file-stream
;  (testing "Import from file stream"
;    (let [current-id (atom 0)
;          file-stream (slurp (str u/examples-folder "small-example.xml"))]
;      (is (= (imp/import-file-stream
;              file-stream
;              #(swap! current-id inc))
;             esr/expected-small-example)))))

;(deftest import-dance-perfect-competition
;  (testing "Import competition from a Dance Perfect file"
;    (is (let [current-id (atom 0)]
;          (= (imp/competition-xml->map u/small-example #(swap! current-id inc))
;             esr/expected-small-example)))
;
;    (is (let [current-id (atom 0)]
;          (= (imp/competition-xml->map u/real-example #(swap! current-id inc))
;             u/expected-real-example)))
;
;    (is (let [current-id (atom 0)]
;          (= (imp/competition-xml->map u/real-example-kungsor #(swap! current-id inc))
;             u/expected-real-example-kungsor)))
;
;    (is (let [current-id (atom 0)]
;          (= (imp/competition-xml->map u/real-example-uppsala #(swap! current-id inc))
;             u/expected-real-example-uppsala)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests for different parts of a competition

;(deftest import-rounds
;  (testing "Import rounds"
;    (let [current-id (atom 0)]
;      (is (= (map :class/rounds
;                  (:competition/classes
;                   (imp/competition-xml->map u/small-example #(swap! current-id inc))))
;             (map :class/rounds
;                  (:competition/classes
;                   esr/expected-small-example)))))))

;(deftest import-classes
;  (testing "Import classes"
;    (let [current-id (atom 0)]
;      (is (= (:competition/classes
;              (imp/competition-xml->map u/small-example #(swap! current-id inc)))
;             (:competition/classes
;              esr/expected-small-example))))))

(deftest import-adjudicators
  (testing "Import of adjudicators"
    (let [current-id (atom 0)]
      (is (= (:competition/adjudicators
              (imp/competition-xml->map u/small-example #(swap! current-id inc)))
             (:competition/adjudicators
              esr/expected-small-example))))))

(deftest import-dance-perfect-panels
  (testing "Import of adjudicator panels"
    (let [current-id (atom 0)]
      (is (= (:competition/panels
              (imp/competition-xml->map u/small-example #(swap! current-id inc)))
             (:competition/panels
             esr/expected-small-example))))))

(deftest import-dance-perfect-competition-data
  (testing "Import competition data of Dance Perfect file"
    (let [current-id (atom 0)]
      (is (= (select-keys (imp/competition-xml->map u/small-example #(swap! current-id inc))
                          [:competition/name :competition/date :competition/location :competition/options])
             {:competition/name "TurboMegatävling"
              :competition/date (tcr/to-date (tc/date-time 2014 11 22))
              :competition/location "THUNDERDOME"
              :competition/options
              {:dance-competition/adjudicator-order-final true
               :dance-competition/adjudicator-order-other true
               :dance-competition/heat-text-on-adjudicator-sheet true
               :dance-competition/same-heat-all-dances true
               :dance-competition/skip-adjudicator-letter true
               :dance-competition/name-on-number-sign true,
               :dance-competition/club-on-number-sign true,
               :dance-competition/random-order-in-heats true
                              
               :presentation/arial-font "SimSun"
               :presentation/courier-font "NSimSun"
               :presentation/chinese-fonts true
               
               :printer/preview true
               :printer/printer-select-paper true}})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Edge tests
(deftest import-file-stream-edges
  (testing "Input should be validated"
    (let [file-stream (slurp (str u/examples-folder "small-example.xml"))]
      (is (thrown? java.lang.AssertionError
                   (imp/import-file-stream nil nil)))

      (is (thrown? java.lang.AssertionError
                   (imp/import-file-stream file-stream nil)))

      (is (thrown? java.lang.AssertionError
                   (imp/import-file-stream nil #(inc 1)))))))

(deftest import-competition-edges
  (testing "Input should be validated"
    (is (thrown? java.lang.AssertionError
                 (imp/competition-xml->map nil nil)))

    (is (thrown? java.lang.AssertionError
                 (imp/competition-xml->map u/small-example nil)))

    (is (thrown? java.lang.AssertionError
                 (imp/competition-xml->map nil #(inc 1))))))


