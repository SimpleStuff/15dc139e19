(ns tango.export-tests
  (:require [clojure.test :refer :all]            
            [tango.import :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [tango.test-data :as data]))

; (clojure.test/run-tests 'tango.export-tests)

(declare small-exampel-data)

(deftest export-dance-perfect-file
  (testing "Export of a Dance Perfect xml file"
    (let [imported-file (imp/import-file "./test/tango/small-example.xml")
          ;exported-file (imp/export-file (:file/version imported-file) (:file/content imported-file))
          xml-data (imp/data->dance-perfect-xml (:file/version imported-file) (:file/content imported-file))]
      (is (= (imp/read-xml "./test/tango/small-example.xml")
             xml-data)))))

(def small-exampel-data
  {:file/version "4.1",
   :file/content
   {:competition/name "TurboMegatävling",
    :competition/location "THUNDERDOME",
    :competition/date #inst "2014-11-22T00:00:00.000-00:00",
    :competition/classes
    [{:class/name "Hiphop Singel Star B", :class/competitors
      [{:competitor/name "Rulle Trulle", :competitor/number 1, :competitor/club "Rulles M&M"}
       {:competitor/name "Katchyk Wrong", :competitor/number 2, :competitor/club "Sccchhh"}]}
     {:class/name "Hiphop Singel Star J Fl", :class/competitors
      [{:competitor/name "Ringo Stingo", :competitor/number 20, :competitor/club "Kapangg"}
       {:competitor/name "Greve Turbo", :competitor/number 21, :competitor/club "OOoost"}]}]},
   :file/import-status :success, :file/import-errors []})
