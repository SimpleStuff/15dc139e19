(ns tango.file-resource-tests
  (:require [clojure.test :refer :all]
            [tango.test-utils :as u]
            [tango.file-resource :as fp]))

(def imported-competition u/expected-real-example)
(def file-path "./test-file.dat")

(defn remove-files [test-fn]
  (test-fn)
  (clojure.java.io/delete-file file-path true))

;; :each
(use-fixtures :once remove-files)

;; http://www.compoundtheory.com/clojure-edn-walkthrough/
(deftest save-competition-to-file
  (testing "Save a competition model to file"
    (is (= nil (fp/save imported-competition file-path)))
    (is (= imported-competition (fp/read-file file-path)))))
