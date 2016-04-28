(ns tango.domain)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Makers

(defn make-competition
  [name date location options panels adjudicators activites classes]
  {:competition/name name
   :competition/date date
   :competition/location location
   :competition/options options
   :competition/panels panels
   :competition/adjudicators adjudicators
   :competition/activities activites
   :competition/classes classes})

(defn make-competition-data
  [name date location options]
  {:competition/name name
   :competition/date date
   :competition/location location
   :competition/options options})

(defn make-adjudicator
  [id name country number]
  {:adjudicator/id id
   :adjudicator/name name
   :adjudicator/country country
   :adjudicator/number number})

(defn make-activity [name number comment id position source-id time]
  {:activity/name name
   :activity/number number
   :activity/comment comment
   :activity/id id
   :activity/position position
   :activity/source-id source-id
   :activity/time time})