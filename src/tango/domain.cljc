(ns tango.domain
  (:require [schema.core :as s]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Domain entities
;(s/defrecord Adjudicator
;  [name :- s/Str])

(def adjudicator
  "A schema describing an adjudicator"
  {:adjudicator/name s/Str
   :adjudicator/id s/Uuid
   :adjudicator/country s/Str
   :adjudicator/number s/Int})

(def adjudicator-panel
  {:adjudicator-panel/id s/Uuid
   :adjudicator-panel/name s/Str
   :adjudicator-panel/adjudicators [adjudicator]})

(def dance
  {:dance/name s/Str})

(def participant
  {:participant/id s/Uuid
   :participant/name s/Str
   :participant/club s/Str
   :participant/number s/Int})

(def result
  {:result/mark-x      s/Bool
   :result/point       s/Int
   :result/participant participant
   :result/adjudicator adjudicator
   (s/optional-key :result/id) s/Uuid})

;; TODO - round-types should be consolidated with import
(def round-types
  [:round-type/none :round-type/normal-x :round-type/semifinal-x
   :round-type/final-x :round-type/b-final-x :round-type/retry-x
   :round-type/second-try-x
   :round-type/presentation])

(def round-types-enum
  (apply s/enum round-types))

(def statuses
  [:status/not-started
   :status/completed])

(def status-enum
  (apply s/enum statuses))

(def round
  {:round/id                          s/Uuid
   :round/status                      status-enum
   :round/number-to-recall            s/Int
   :round/panel                       adjudicator-panel
   :round/dances                      [dance]
   :round/index                       s/Int
   (s/optional-key :round/starting)   [participant]
   :round/type                        round-types-enum
   :round/number                      s/Str
   (s/optional-key :round/start-time) s/Inst
   :round/number-of-heats             s/Int
   (s/optional-key :round/results)    [result]})

(def class-schema
  {:class/name                      s/Str
   :class/id                        s/Uuid
   :class/position                  s/Int
   :class/adjudicator-panel         adjudicator-panel
   (s/optional-key :class/dances)   [dance]
   (s/optional-key :class/starting) [participant]
   (s/optional-key :class/rounds)   [round]})

(def activity-schema
  {:activity/id                      s/Uuid
   :activity/position                s/Int
   :activity/name                    s/Str
   :activity/number                  s/Str
   :activity/comment                 s/Str
   (s/optional-key :activity/time)   s/Inst
   (s/optional-key :activity/source) round})

(def printer-options
  {:printer/preview s/Bool
   :printer/printer-select-paper s/Bool})

(def presentation-options
  {:presentation/chinese-fonts s/Bool
   :presentation/courier-font s/Str
   :presentation/arial-font s/Str})

(def dance-competition-options
  {:dance-competition/same-heat-all-dances s/Bool
   :dance-competition/heat-text-on-adjudicator-sheet s/Bool
   :dance-competition/name-on-number-sign s/Bool
   :dance-competition/skip-adjudicator-letter s/Bool
   :dance-competition/adjudicator-order-final s/Bool
   :dance-competition/random-order-in-heats s/Bool
   :dance-competition/club-on-number-sign s/Bool
   :dance-competition/adjudicator-order-other s/Bool})

(def competition-data-schema
  {:competition/id       s/Uuid
   :competition/name     s/Str
   :competition/location s/Str
   :competition/date     s/Inst
   :competition/options  (merge printer-options presentation-options dance-competition-options)})

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

;; TODO - move utils to its own file
(defn- distribute [coll n]
  ;; Distributes the elements in n vectors as evenly as possible
  ;; Each vector will have m or m+1 elements, where m is (/ (count coll n))
  ;; The order of the elements is preserved in each group.
  (let [c (count coll)
        m (int (/ c n))
        k (mod c n)
        big-counts (repeat k (inc m))
        small-counts (repeat (- n k) m)]
    (vec (concat big-counts small-counts))))

;; TODO - guess this could be more elegant..
(defn create-distribution [coll n]
  (let [distribution-sizes (distribute coll n)]
    (reduce
      (fn [x y]
        (conj x (vec (take y (drop (count (flatten x)) coll)))))
      []
      distribution-sizes)))