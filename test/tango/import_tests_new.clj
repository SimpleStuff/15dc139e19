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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Setup

(def competition-snippet
  (zip/xml-zip (xml/parse (clojure.java.io/file "./test/tango/test-snippets.xml"))))

(def expected-lookup-table
  {:adjudicator-index
   {:0 1
    :1 2
    :2 3}})

(def expected-adjudicators
  [{:adjudicator/name "Anders"
    :adjudicator/id 1
    :adjudicator/country "Sweden"
    :dp/temp-id 0}
   {:adjudicator/name "Bertil"
    :adjudicator/id 2
    :adjudicator/country ""
    :dp/temp-id 1}
   {:adjudicator/name "Cesar"
    :adjudicator/id 3
    :adjudicator/country ""
    :dp/temp-id 2}])

(def expected-panels
  [{:adjudicator-panel/name "1"
    :adjudicator-panel/adjudicators
    [{:adjudicator/name "Anders"
      :adjudicator/id 1
      :adjudicator/country "Sweden"}
     {:adjudicator/name "Bertil"
      :adjudicator/id 2
      :adjudicator/country ""}]
    :adjudicator-panel/id 1}

   {:adjudicator-panel/name "2"
    :adjudicator-panel/adjudicators
    [{:adjudicator/name "Bertil"
      :adjudicator/id 2
      :adjudicator/country ""}
     {:adjudicator/name "Cesar"
      :adjudicator/id 3
      :adjudicator/country ""}]
    :adjudicator-panel/id 2}])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests

(deftest import-adjudicator
  (testing "Import of adjudicator part"
    (let [current-id (atom 0)]
      (is (= (imp/adjudicators-xml->map competition-snippet #(swap! current-id inc))
             expected-adjudicators)))))

(deftest import-dance-perfect-panels
  (testing "Import of adjudicator panels"
    (let [current-id (atom 0)]
      (is (= (imp/adjudicator-panels-xml->map
              competition-snippet
              #(swap! current-id inc)
              expected-adjudicators)
             expected-panels)))))

(deftest import-dance-perfect-competition-data
  (testing "Import competition data of Dance Perfect file"
    (is (= (imp/competition-data-xml->map competition-snippet)
           {:competition/name "TurboMegatävling"
            :competition/date (tcr/to-date (tc/date-time 2014 11 22))
            :competition/location "THUNDERDOME"}))))

(deftest import-dance-perfect-competition
  (testing "Import competition name of Dance Perfect file"
    (let [current-id (atom 0)]
      (is (= (imp/competition-xml->map competition-snippet #(swap! current-id inc))
             {:competition/name "TurboMegatävling"
              :competition/date (tcr/to-date (tc/date-time 2014 11 22))
              :competition/location "THUNDERDOME"
              :competition/panels
              [{:adjudicator-panel/name "1"
                :adjudicator-panel/adjudicators
                [{:adjudicator/name "Anders"
                  :adjudicator/id 1
                  :adjudicator/country "Sweden"}
                 {:adjudicator/name "Bertil"
                  :adjudicator/id 2
                  :adjudicator/country ""}]
                :adjudicator-panel/id 4}
               
               {:adjudicator-panel/name "2"
                :adjudicator-panel/adjudicators
                [{:adjudicator/name "Bertil"
                  :adjudicator/id 2
                  :adjudicator/country ""}
                 {:adjudicator/name "Cesar"
                  :adjudicator/id 3
                  :adjudicator/country ""}]
                :adjudicator-panel/id 5}]

              :competition/adjudicators
              [{:adjudicator/name "Anders"
                :adjudicator/id 1
                :adjudicator/country "Sweden"}
               {:adjudicator/name "Bertil"
                :adjudicator/id 2
                :adjudicator/country ""}
               {:adjudicator/name "Cesar"
                :adjudicator/id 3
                :adjudicator/country ""}]
              :competitor/activities []
              :competition/classes []})))))
