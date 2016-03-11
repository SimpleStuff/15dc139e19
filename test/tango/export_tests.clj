(ns tango.export-tests
  (:require [clojure.test :refer :all]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [tango.export :as e]
            [tango.test-utils :as u]
            ))

(deftest test-export
  (testing "Export from test data"
    (let [x 42]
      (is (= x (e/export u/transact-small-example))))))

