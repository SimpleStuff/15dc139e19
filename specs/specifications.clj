;; ## Tango information model example
;; Small information model example.
(ns tango.specifications
  (:require [clj-time.core :as tc]
            [clj-time.coerce :as tcr]))

;; To view a prety printed version, load this file into a REPL and
;; run the following :
;; `(clojure.pprint/pprint example-competition-1)`

(def example-participant-1
  "Describes a participant.

  `:participant/number` represents the number given to the participant in this competition."
  {:participant/name "Participant 1"
   :participant/id "1"
   :participant/number "1"
   :participant/club "Club"})

(def example-dance-1
  "Describes a dance."
  {:dance/name "Dance 1"})

(def example-adjudicator-1
  "Describes an adjudicator."
  {:adjudicator/name "Adjudicator 1"
   :adjudicator/id "1"
   :adjudicator/country "Sweden"})

(def example-panel-1
  "Describes a adjudicator-panel.

  A panel is a collection of adjudicators that will be judging a class or a round."
  {:adjudicator-panel/name "Panel 1"
   :adjudicator-panel/adjudicators [example-adjudicator-1]
   :adjudicator-panel/id "1"})

(def example-activity-1
  {:activity/name "Round 1"
   :activity/number "1A"
   :activity/comment "Comment"
   :activity/id "1"
   :activity/position "1"})

(def example-marks-1
  {:mark/x true
   :mark/d3 false
   :mark/a false
   :mark/b false
   :mark/placing 1})

(def example-judging-1
  {:judging/marks [example-marks-1]
   :judging/adjudicator example-adjudicator-1})

(def example-result-1
  {:result/participant [example-participant-1]
   :result/judgings [example-judging-1]
   :result/recalled true})

(def example-round-1
  {:round/activity [example-activity-1]
   :round/starting [example-participant-1]
   :round/start-time (tcr/to-date (tc/date-time 2014 11 22))
   :round/panel [example-panel-1]
   :round/results [example-result-1]
   :round/recall 5
   :round/number 1 ;; the rounds number in its class
   :round/heats 4
   :round/status :not-started
   :round/dances [example-dance-1]
   :round/type :semi-final})

(def example-class-1
  {:class/name "Class 1"
   :class/dances [example-dance-1]
   :class/position "1"
   :class/starting [example-participant-1]
   :class/remaining []
   :class/rounds [example-round-1]})

(def example-competition-1
  {:competition/name "Competition name"
   :competition/date (tcr/to-date (tc/date-time 2014 11 22))
   :competition/location "Location"
   :competition/panels [example-panel-1]
   :competition/adjudicators [example-adjudicator-1]
   :competitor/activities [example-round-1]
   :competition/classes [example-class-1]})
