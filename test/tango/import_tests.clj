(ns tango.import-tests
  (:require [clojure.test :refer :all]
            ;[tango.core :refer :all]
            [tango.import :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [tango.test-data :as data]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup

(def competition-snippet
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/test-snippets.xml"))))

(def expected-small-competition
  (read-string (slurp "./test/tango/expected_small_result.clj.test")))

(def real-snippet
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/real-example.xml"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests

(deftest import-rounds
  (testing "Import rounds"
    (let [current-id (atom 0)]
      (is (= (map :class/rounds
                  (:competition/classes
                   (imp/competition-xml->map competition-snippet #(swap! current-id inc))))
             (map :class/rounds
                  (:competition/classes
                   expected-small-competition)))))))

(deftest import-classes
  (testing "Import classes"
    (let [current-id (atom 0)]
      (is (= (:competition/classes
              (imp/competition-xml->map competition-snippet #(swap! current-id inc)))
             (:competition/classes
              expected-small-competition))))))

(deftest import-adjudicators
  (testing "Import of adjudicators"
    (let [current-id (atom 0)]
      (is (= (:competition/adjudicators
              (imp/competition-xml->map competition-snippet #(swap! current-id inc)))
             (:competition/adjudicators
              expected-small-competition))))))

(deftest import-dance-perfect-panels
  (testing "Import of adjudicator panels"
    (let [current-id (atom 0)]
      (is (= (:competition/panels
              (imp/competition-xml->map competition-snippet #(swap! current-id inc)))
             (:competition/panels
              expected-small-competition))))))

(deftest import-dance-perfect-competition-data
  (testing "Import competition data of Dance Perfect file"
    (let [current-id (atom 0)]
      (is (= (select-keys (imp/competition-xml->map competition-snippet #(swap! current-id inc))
                          [:competition/name :competition/date :competition/location])
             {:competition/name "TurboMegatävling"
              :competition/date (tcr/to-date (tc/date-time 2014 11 22))
              :competition/location "THUNDERDOME"})))))

(deftest import-dance-perfect-competition
  (testing "Import competition name of Dance Perfect file"
    (let [current-id (atom 0)]
      (is (= (imp/competition-xml->map competition-snippet #(swap! current-id inc))
             expected-small-competition)))))

;; (let [current-id (atom 0)]
;;   (imp/competition-xml->map real-snippet #(swap! current-id inc)))
