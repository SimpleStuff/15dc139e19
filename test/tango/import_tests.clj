(ns tango.import-tests
  (:require [clojure.test :refer :all]
            ;[tango.core :refer :all]
            [tango.import :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [tango.test-data :as data]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import

; (clojure.test/run-tests 'tango.import-tests)

(def small-exampel-xml (xml/parse (clojure.java.io/file "./test/tango/small-example.xml")))

(def large-exampel-xml (xml/parse (clojure.java.io/file "./test/tango/DPTest.xml")))

(deftest import-dance-perfect-file
  (testing "Import of a Dance Perfect xml file"
    (let [imported-file (imp/import-file "./test/tango/small-example.xml")]
      (is (= (:file/version imported-file)
             "4.1"))
      (is (= (:file/import-status imported-file)
             :success))
      (is (= (:file/import-errors imported-file)
             []))
      (is (= (:file/metadata imported-file)
             data/small-file-expected-metadata))
      (is (= (:file/content imported-file)
             data/small-file-expected-content)))))

(deftest import-dance-perfect-file-with-results
  (testing "Import of a Dance Perfect xml file with results"
    (let [imported-file (imp/import-file "./test/tango/results-example.xml")]
      (is (= (:file/version imported-file)
             "4.1"))
      (is (= (:file/import-status imported-file)
             :success))
      (is (= (:file/import-errors imported-file)
             []))
      (is (= (:file/content imported-file)
             data/results-file-expected-content)))))

(deftest import-dance-perfect-file-with-events
  (testing "Import of a Dance Perfect xml file with events"
    (let [imported-file (imp/import-file "./test/tango/event-example.xml")]
      (is (= (:file/version imported-file)
             "4.1"))
      (is (= (:file/import-status imported-file)
             :success))
      (is (= (:file/import-errors imported-file)
             []))
      (is (= (:file/content imported-file)
             data/events-file-expected-content)))))

(deftest convert-round-values
  (testing "Convert DP round int values to keys"
    (is (= (mapv imp/round-value->key (range 0 22))
           [:none
            :normal-x :semifinal-x :final-x :b-final-x :retry-x :second-try-x
            :normal-1-5 :semifinal-1-5 :retry-1-5 :second-try-1-5
            :normal-3d :semifinal-3d :retry-3d :second-try-3d
            :normal-a+b :semifinal-a+b :final-a+b :b-final-a+b :retry-a+b :second-try-a+b
            :presentation]))))

;; TODO - this test gets to unwieldy when data is changed, re-think
;;  could be interesting to look at prismatic schema generation
;; (deftest import-large-dance-perfect-file
;;   (testing "Import of a large Dance Perfect xml file"
;;     (let [imported-file (imp/import-file "./test/tango/DPTest.xml")]
;;       (is (= imported-file
;;              data/large-file-expected-content)))))

(deftest import-of-path-that-do-not-exist
  (testing "Import of an invalid file path"
    (let [imported-file (imp/import-file "bad-path"
                                         )]
      (is (= (:file/version imported-file)
             ""))
      (is (= (:file/import-status imported-file)
             :failed))
      (is (= (:file/import-errors imported-file)
             [:file-not-found]))
      (is (= (:file/content imported-file)
             [])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; event post processing

(deftest post-processing-of-events
  (testing "Testing adaptions of events to fix DP format quirks"
   (let [events [{:event/class-number 1 :event/position 1}
                 {:event/class-number 2 :event/position 3}
                 {:event/class-number 1 :event/position 2}]
         classes [{:class/position 1 :class/competitors
                   [{:competitor/name "Rulle Trulle"
                     :competitor/club "MegaTurbo"
                     :competitor/number 30
                     :competitor/position 1}
                    {:competitor/name "Robot Lund"
                     :competitor/club "Skrot"
                     :competitor/number 31
                     :competitor/position 2}]}
                  {:class/position 2 :class/competitors []}]]
     (is (= (imp/event-list-post-process events classes)
            [{:event/class-number 1,
              :event/position 1,
              :event/nrof-events-in-class 2,
              :event/class-index 0,
              :event/starting 2}
             {:event/class-number 1,
              :event/position 2,
              :event/nrof-events-in-class 2,
              :event/class-index 1,
              :event/starting 2}
             {:event/class-number 2,
              :event/position 3,
              :event/nrof-events-in-class 1,
              :event/class-index 0,
              :event/starting 0}])))))

(deftest post-processing-of-events-should-not-count-presentations
  (testing "Event number for a class should not count 'presentation' events"
   (let [events [{:event/class-number 1 :event/position 1 :event/round :presentation}
                 {:event/class-number 1 :event/position 2 :event/round :normal}]
         classes [{:class/position 1 :class/competitors
                   [{:competitor/name "Rulle Trulle"
                     :competitor/club "MegaTurbo"
                     :competitor/number 30
                     :competitor/position 1}]}
                  {:class/position 2 :class/competitors []}]]
     (is (= (imp/event-list-post-process events classes)
            [{:event/class-number 1,
              :event/position 1,
              :event/round :presentation
              :event/nrof-events-in-class 1,
              :event/class-index 0,
              :event/starting 1}
             {:event/class-number 1,
              :event/position 2,
              :event/round :normal,
              :event/nrof-events-in-class 1,
              :event/class-index 1,
              :event/starting 1}])))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest read-dance-perfect-competition-name
  (testing "Import competition name of Dance Perfect file"
    (is (= (:competition/name (imp/dance-perfect-xml->data small-exampel-xml))
           "TurboMegatävling"))))

(deftest read-dance-perfect-competition-date
  (testing "Import competition date of Dance Perfect file"
    (is (= (:competition/date (imp/dance-perfect-xml->data small-exampel-xml))
           (tcr/to-date (tc/date-time 2014 11 22))))
    (is (= (class (:competition/date (imp/dance-perfect-xml->data small-exampel-xml)))
           java.util.Date))))

(deftest read-dance-perfect-competition-place
  (testing "Import competition name of Dance Perfect file"
    (is (= (:competition/location (imp/dance-perfect-xml->data small-exampel-xml))
           "THUNDERDOME"))))

(deftest read-dance-perfect-competition-classes
  (testing "Import competition classes of Dance Perfect file"
    (is (= (:competition/classes (imp/dance-perfect-xml->data small-exampel-xml))
           (:competition/classes data/small-file-expected-content)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Files that have caused problems
(deftest file-with-class-with-empty-start-list
  (testing "File that have been known to produce error when imported, it contains a class representing \"Unused Couples\" wich contains empty list of dancers and startlist"
    (let [imported-file (imp/import-file "./test/tango/unused-couples.xml")]
      (is (= (:file/import-status imported-file)
             :success))
      (is (= (:file/import-errors imported-file)
             [])))))

;; TODO - file that do not exist
;; TODO - call with path that is not string
