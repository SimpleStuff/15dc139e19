(ns tango.test-utils
  (:require [clojure.xml :as xml]
            [clojure.zip :as zip]))

(def expected-folder "./test/tango/expected/")

(def examples-folder "./test/tango/examples/")

(defn- slurp-expected [file-name]
  (read-string (slurp (str expected-folder file-name))))

(defn- parse-examples [file-name]
  (zip/xml-zip (xml/parse (clojure.java.io/file (str examples-folder file-name)))))

(def small-example
  (parse-examples "small-example.xml"))

(def expected-small-example
  (slurp-expected "expected_small_result.clj.test"))

(def real-example
  (parse-examples "real-example.xml"))

(def expected-real-example
  (slurp-expected "expected_real_example_result.clj.test"))

(def expected-real-example-presentation
  (slurp-expected "expected_real_example_presentation.clj.test"))

(def real-example-kungsor
  (parse-examples "real-example-kungsor.xml"))

(def expected-real-example-kungsor
  (slurp-expected "expected_real_example_kungsor_result.clj.test"))

(def expected-real-example-kungsor-presentation
  (slurp-expected "expected_real_example_kungsor_presentation.clj.test"))

(def real-example-uppsala
  (parse-examples "real-example-uppsala.xml"))

(def expected-real-example-uppsala
  (slurp-expected "expected_real_example_uppsala_result.clj.test"))

(def expected-real-example-uppsala-presentation
  (slurp-expected "expected_real_example_uppsala_presentation.clj.test"))


