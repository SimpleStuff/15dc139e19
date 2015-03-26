(ns tango.export-tests
  (:require [clojure.test :refer :all]            
            [tango.import :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [tango.test-data :as data]))

; (clojure.test/run-tests 'tango.export-tests)

(declare small-exampel-data)
(declare small-exampel-xml)

(deftest export-dance-perfect-file
  (testing "Given an imported DP-file When exported Then the same data is written to file"
    (let [imported-data (imp/import-file "./test/tango/small-example.xml")
          xml-data-to-export (imp/data->dance-perfect-xml-data
                              (:file/version imported-data)
                              (:file/content imported-data))]
      ;; xml-data map should be equal to import data map
      (is (= (imp/read-xml "./test/tango/small-example.xml")
             xml-data-to-export))
      ;; emitted xml should be correct
      (is (= (imp/data->dance-perfect-xml (:file/version imported-data)
                                          (:file/content imported-data))
             small-exampel-xml)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test data

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

(def small-exampel-xml
"<?xml version='1.0' encoding='UTF-8'?>
<DancePerfect Version='4.1'>
<CompData Name='TurboMegatävling' Date='2014-11-22' Place='THUNDERDOME'/>
<AdjPanelList>
<AdjList/>
</AdjPanelList>
<ClassList>
<Class Name='Hiphop Singel Star B' Seq='0'>
<StartList Qty='2'>
<Couple Name='Rulle Trulle' Seq='0' License='' Club='Rulles M&M' Number='1'/>
<Couple Name='Katchyk Wrong' Seq='1' License='' Club='Sccchhh' Number='2'/>
</StartList>
</Class>
<Class Name='Hiphop Singel Star J Fl' Seq='1'>
<StartList Qty='2'>
<Couple Name='Ringo Stingo' Seq='0' License='' Club='Kapangg' Number='20'/>
<Couple Name='Greve Turbo' Seq='1' License='' Club='OOoost' Number='21'/>
</StartList>
</Class>
</ClassList>
</DancePerfect>
")
