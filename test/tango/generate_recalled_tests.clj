(ns tango.generate-recalled-tests
  (:require [clojure.test :refer :all]
            [tango.generate-recalled :as gen]
            [tango.import :as import]
            [tango.test-utils :as u]))

(def test-data (import/competition-xml->map u/generate-example #(java.util.UUID/randomUUID)))

(def result-html
  "<head><title>Last Recalled</title><style type=\"text/css\">#stor { Font-size: 54px; } #liten { Font-size: 36px; }</style></head><body><h1><font id=\"stor\">Event 3A</font></h1><h1><font id=\"stor\">Disco Par Silver B1 / B2</font></h1><h1><font id=\"liten\">Recalled from Round 1</font></h1><h1><font id=\"stor\">1880, 1881, 1882, 1883, 1884, 1885, 1886, 1887, 1888, 1889, 1890, 1891, 1892, 1893, 1894, 1895, 1896, 1897, 1907, 1909</font></h1><br /><br /><br /><br /><br /></body>")

(deftest generate-recalled
  (testing "Genereate recalled html from imported data"
    (let [import-data test-data]
      (is (= [{:activity/number "3A"
               :html            result-html}]
             (gen/generate-recalled-html import-data))))))

(defn mock-spit-succsess[f content]
  (testing "-> Always ok"
    (is true)))

(defn mock-spit-fail [f content]
  (testing "-> Not to be invoked"
    (is false)))

(defn mock-spit [f content]
  (testing "-> Valid html file name"
    (is (= "nv_re_3A.htm" f)))
  (testing "-> Valid html content"
    (is (= result-html content))))

(deftest write-recalled-0-new-round
  (testing "Write recalled html 0 new sounds"
    (let [recalled-htmls (gen/generate-recalled-html test-data)]
      (is (= #{"3A"}
             (gen/write-recalled-html #{"3A"} recalled-htmls mock-spit-fail))))))

(deftest write-recalled-1-new-round
  (testing "Write recalled html 1 new round"
    (let [recalled-htmls (gen/generate-recalled-html test-data)]
      (is (= #{"3A"}
             (gen/write-recalled-html #{} recalled-htmls mock-spit))))))

(deftest write-recalled-2-new-round
  (testing "Write recalled html 2 new rounds"
    (let [recalled-htmls (gen/generate-recalled-html test-data)]
      (is (= #{"3A" "3B"}
             (gen/write-recalled-html #{} [{:activity/number "3A"
                                            :html "aaa"}
                                           {:activity/number "3B"
                                            :html "aaa"}] mock-spit-succsess))))))

;"5A"

;(gen/generate-html (gen/find-last-completed test-data))

;(gen/find-last-completed test-data)

;(map gen/generate-html (mapv #(gen/make-completed % (:competition/classes test-data)) (gen/find-completed test-data)))
;
;(spit "html-test.html" (gen/generate-html (gen/find-last-completed test-data)))

;(gen/generate-recalled-html test-data)
