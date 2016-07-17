(ns tango.cljs.testing
  (:require [cljs.test :refer-macros [deftest is testing run-tests]]
            [tango.cljs.runtime.read :as r]))

(deftest first-test
  (is (= 1 1)))

(deftest failing
  (is (= 0 1)))
