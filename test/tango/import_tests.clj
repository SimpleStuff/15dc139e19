(ns tango.import-tests
  (:require [clojure.test :refer :all]
            ;[tango.core :refer :all]
            [tango.import :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup

(def small-example
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/small-example.xml"))))

(def expected-small-example
  (read-string (slurp "./test/tango/expected_small_result.clj.test")))

(def real-example
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/real-example.xml"))))

(def expected-real-example
  (read-string (slurp "./test/tango/expected_real_example_result.clj.test")))

(def real-example-kungsor
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/real-example-kungsor.xml"))))

(def expected-real-example-kungsor
  (read-string (slurp "./test/tango/expected_real_example_kungsor_result.clj.test")))

(def real-example-uppsala
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/real-example-uppsala.xml"))))

(def expected-real-example-uppsala
  (read-string (slurp "./test/tango/expected_real_example_uppsala_result.clj.test")))

;; Generate example data
(defn generate-expected [name data]
  (spit (str "expected_" name "_result.clj.test")
        (pr-str (let [current-id (atom 0)]
                  (imp/competition-xml->map data #(swap! current-id inc))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests of complete competitions

(deftest import-dance-perfect-competition
  (testing "Import competition name of Dance Perfect file"  
    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map small-example #(swap! current-id inc))
             expected-small-example)))

    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map real-example #(swap! current-id inc))
             expected-real-example)))

    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map real-example-kungsor #(swap! current-id inc))
             expected-real-example-kungsor)))

    (is (let [current-id (atom 0)]
          (= (imp/competition-xml->map real-example-uppsala #(swap! current-id inc))
             expected-real-example-uppsala)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests for different parts of a competition

(deftest import-rounds
  (testing "Import rounds"
    (let [current-id (atom 0)]
      (is (= (map :class/rounds
                  (:competition/classes
                   (imp/competition-xml->map small-example #(swap! current-id inc))))
             (map :class/rounds
                  (:competition/classes
                   expected-small-example)))))))

(deftest import-classes
  (testing "Import classes"
    (let [current-id (atom 0)]
      (is (= (:competition/classes
              (imp/competition-xml->map small-example #(swap! current-id inc)))
             (:competition/classes
              expected-small-example))))))

(deftest import-adjudicators
  (testing "Import of adjudicators"
    (let [current-id (atom 0)]
      (is (= (:competition/adjudicators
              (imp/competition-xml->map small-example #(swap! current-id inc)))
             (:competition/adjudicators
              expected-small-example))))))

(deftest import-dance-perfect-panels
  (testing "Import of adjudicator panels"
    (let [current-id (atom 0)]
      (is (= (:competition/panels
              (imp/competition-xml->map small-example #(swap! current-id inc)))
             (:competition/panels
              expected-small-example))))))

(deftest import-dance-perfect-competition-data
  (testing "Import competition data of Dance Perfect file"
    (let [current-id (atom 0)]
      (is (= (select-keys (imp/competition-xml->map small-example #(swap! current-id inc))
                          [:competition/name :competition/date :competition/location])
             {:competition/name "TurboMegatävling"
              :competition/date (tcr/to-date (tc/date-time 2014 11 22))
              :competition/location "THUNDERDOME"})))))

