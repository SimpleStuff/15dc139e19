(ns tango.import-tests-new
  (:require [clojure.test :refer :all]
            ;[tango.core :refer :all]
            [tango.import-new :as imp]
            [clj-time.core :as tc]
            [clj-time.coerce :as tcr]
            [clojure.xml :as xml]
            [tango.test-data :as data]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]))

;; {:competition/name "Competition name"
;;    :competition/date (tcr/to-date (tc/date-time 2014 11 22))
;;    :competition/location "Location"
;;    :competition/panels [example-panel-1]
;;    :competition/adjudicators [example-adjudicator-1]
;;    :competitor/activities [example-round-1]
;;    :competition/classes [example-class-1]}

; (def small-exampel-xml (xml/parse (clojure.java.io/file "./test/tango/small-example.xml")))

(def competition-snippet
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/test-snippets.xml"))))

;; need to do first
(deftest import-adjudicator
  (testing "Import of adjudicator part"
    (is (= (imp/adjudicators->map (zx/xml-> competition-snippet
                                            :AdjPanelListSnippets :AdjPanelList))
           [{:adjudicator/name "Anders"}]))))

(deftest import-dance-perfect-competition
  (testing "Import competition name of Dance Perfect file"
    (is (= (imp/competition->map (first (zx/xml-> competition-snippet :CompDataSnippets :CompData)))
           {:competition/name "TurboMegatävling"
            :competition/date (tcr/to-date (tc/date-time 2014 11 22))
            :competition/location "THUNDERDOME"
            :competition/panels []
            :competition/adjudicators []
            :competitor/activities []
            :competition/classes []}))))
