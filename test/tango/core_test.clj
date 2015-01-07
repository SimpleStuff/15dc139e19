(ns tango.core-test
  (:require [clojure.test :refer :all]
            [tango.core :refer :all]
            [tango.import :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import

; (clojure.test/run-tests 'tango.core-test)

(def small-exampel-xml (xml/parse (clojure.java.io/file "./test/tango/small-example.xml")))

(def small-file-expected-content
  {:competition/name "TurboMegatävling"
   :competition/date (tcr/to-date (tc/date-time 2014 11 22))
   :competition/location "THUNDERDOME"
   :competition/classes
   [{:class/name "Hiphop Singel Star B"
     :class/competitors
     [{:competitor/name "Rulle Trulle"
       :competitor/club "Rulles M&M"
       :competitor/number 1}
      {:competitor/name "Katchyk Wrong"
       :competitor/club "Sccchhh"
       :competitor/number 2}]}
    {:class/name "Hiphop Singel Star J Fl"
     :class/competitors
     [{:competitor/name "Ringo Stingo"
       :competitor/club "Kapangg"
       :competitor/number 20}
      {:competitor/name "Greve Turbo"
       :competitor/club "OOoost"
       :competitor/number 21}]}]})

(deftest import-dance-perfect-file
  (testing "Import of a Dance Perfect xml file"
    (let [imported-file (imp/import-file "./test/tango/small-example.xml"
                                         )]
      (is (= (:file/version imported-file)
             "4.1"))
      (is (= (:file/import-status imported-file)
             :success))
      (is (= (:file/import-errors imported-file)
             []))
      (is (= (:file/content imported-file)
             small-file-expected-content)))))

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
           (:competition/classes small-file-expected-content)))))

;; TODO - file that do not exist
;; TODO - call with path that is not string
