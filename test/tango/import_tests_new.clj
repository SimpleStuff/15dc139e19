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

(def expected-rounds
  [{:round/activity nil ;[example-activity-1]
    :round/starting [] ;[example-participant-1]
    :round/start-time (tcr/to-date (tc/date-time 2014 11 22))
    :round/panel {} ;[example-panel-1]
    :round/results [] ;[example-result-1]
    :round/recall 5
    :round/number 1 ;; the rounds number in its class
    :round/heats 4
    :round/status :not-started
    :round/dances [] ;[example-dance-1]
    :round/type :semi-final}])

(def expected-classes
  [{:class/name "Hiphop Singel Star B"
    :class/position 1
    
    :class/dances
    [{:dance/name "Medium"}
     {:dance/name "Tango"}
     {:dance/name "VienWaltz"}]
    
    :class/starting
    [{:participant/name "Rulle Trulle"
      :participant/id 1
      :participant/number 30
      :participant/club "Sinus"}
     
     {:participant/name "Hush Bush"
      :participant/id 2
      :participant/number 31
      :participant/club "Zilson"}
     
     {:participant/name "Banana Hamock"
      :participant/id 3
      :participant/number 32
      :participant/club "Zzzz"}]
    
    :class/remaining []
    :class/rounds []
    }

   ;; Example two
   {:class/name "Hiphop Singel Star J Fl"
    :class/position 2
    
    :class/dances []
    
    :class/starting
    [{:participant/name "Ringo Stingo"
      :participant/id 4
      :participant/number 20
      :participant/club "Kapangg"}
     
     {:participant/name "Greve Turbo"
      :participant/id 5
      :participant/number 21
      :participant/club "OOoost"}]
    
    :class/remaining []
    :class/rounds []
    }])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tests

(deftest import-rounds
  (testing "Import rounds"
    (let [current-id (atom 0)]
      (is (= (imp/rounds-xml->map competition-snippet
                                  ;(tcr/to-date (tc/date-time 2014 11 22))
                                  #(swap! current-id inc)
                                  expected-classes)
             expected-rounds)))))

(deftest import-classes
  (testing "Import classes"
    (let [current-id (atom 0)]
      (is (= (imp/class-list-post-process
              (imp/classes-xml->map competition-snippet #(swap! current-id inc))
              (imp/rounds-xml->map competition-snippet #(swap! current-id inc)))
             expected-classes)))))

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
