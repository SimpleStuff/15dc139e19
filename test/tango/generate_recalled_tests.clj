(ns tango.generate-recalled-tests
  (:require [clojure.test :refer :all]
            [tango.generate-recalled :as gen]
            [tango.import :as import]
            [tango.test-utils :as u]))

(def test-data (import/competition-xml->map u/real-example-kungsor #(java.util.UUID/randomUUID)))

(deftest generate-recalled
  (testing "Genereate recalled html from imported data"
    (let [import-data test-data]
      (is (= "html" (gen/generate-recalled import-data))))))

;"5A"
