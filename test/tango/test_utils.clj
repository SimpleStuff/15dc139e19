(ns tango.test-utils
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]))

(def expected-folder "./test/tango/expected/")

(def examples-folder "./test/tango/examples/")

(defn- slurp-expected [file-name]
  (read-string (slurp (str expected-folder file-name))))

(defn- parse-examples [file-name]
  (zip/xml-zip (xml/parse (clojure.java.io/file (str examples-folder file-name)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Small example

(def small-example
  (parse-examples "small-example.xml"))

(def expected-small-example
  (slurp-expected "expected_small_result.clj.test"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Real example

(def real-example
  (parse-examples "real-example.xml"))

(def expected-real-example
  (slurp-expected "expected_real_example_result.clj.test"))

(def expected-real-example-activity-presentation
  (slurp-expected "expected_real_example_presentation.clj.test"))

(def expected-real-example-classes-presentation
  (slurp-expected "expected_real_example_classes_presentation.clj.test"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Kungsör example

(def real-example-kungsor
  (parse-examples "real-example-kungsor.xml"))

(def expected-real-example-kungsor
  (slurp-expected "expected_real_example_kungsor_result.clj.test"))

(def expected-real-example-kungsor-activity-presentation
  (slurp-expected "expected_real_example_kungsor_presentation.clj.test"))

(def expected-real-example-kungsor-classes-presentation
  (slurp-expected "expected_real_example_kungsor_classes_presentation.clj.test"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Uppsala example

(def real-example-uppsala
  (parse-examples "real-example-uppsala.xml"))

(def expected-real-example-uppsala
  (slurp-expected "expected_real_example_uppsala_result.clj.test"))

(def expected-real-example-uppsala-activity-presentation
  (slurp-expected "expected_real_example_uppsala_presentation.clj.test"))

(def expected-real-example-uppsala-classes-presentation
  (slurp-expected "expected_real_example_uppsala_classes_presentation.clj.test"))


