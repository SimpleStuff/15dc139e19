(ns tango.domain
  (:require [schema.core :as sch]
            ;[clojure.spec :as s]
            ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Specs

;; (s/def ::round-types
;;   #{:round-type/none :round-type/normal-x :round-type/semifinal-x
;;     :round-type/final-x :round-type/b-final-x :round-type/retry-x
;;     :round-type/second-try-x
;;     :round-type/presentation})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Domain entities
;(s/defrecord Adjudicator
;  [name :- s/Str])

(def adjudicator
  "A schema describing an adjudicator"
  {:adjudicator/name    sch/Str
   :adjudicator/id      sch/Uuid
   :adjudicator/country sch/Str
   :adjudicator/number  sch/Int})

(def adjudicator-panel
  {:adjudicator-panel/id           sch/Uuid
   :adjudicator-panel/name         sch/Str
   :adjudicator-panel/adjudicators [adjudicator]})

(def dance
  {:dance/name sch/Str})

(def participant
  {:participant/id     sch/Uuid
   :participant/name   sch/Str
   :participant/club   sch/Str
   :participant/number sch/Int})

(def result
  {:result/mark-x                sch/Bool
   :result/point                 sch/Int
   :result/participant           participant
   :result/adjudicator           adjudicator
   (sch/optional-key :result/id) sch/Uuid})

;; TODO - round-types should be consolidated with import
(def round-types
  [:round-type/none :round-type/normal-x :round-type/semifinal-x
   :round-type/final-x :round-type/b-final-x :round-type/retry-x
   :round-type/second-try-x
   :round-type/presentation])

(def round-types-enum
  (apply sch/enum round-types))

(def statuses
  [:status/not-started
   :status/completed])

(def status-enum
  (apply sch/enum statuses))

(def round
  {:round/id                            sch/Uuid
   :round/status                        status-enum
   :round/number-to-recall              sch/Int
   :round/panel                         adjudicator-panel
   :round/dances                        [dance]
   :round/index                         sch/Int
   (sch/optional-key :round/starting)   [participant]
   :round/type                          round-types-enum
   :round/number                        sch/Str
   (sch/optional-key :round/start-time) sch/Inst
   :round/number-of-heats               sch/Int
   (sch/optional-key :round/results)    [result]})

(def class-schema
  {:class/name                        sch/Str
   :class/id                          sch/Uuid
   :class/position                    sch/Int
   :class/adjudicator-panel           adjudicator-panel
   (sch/optional-key :class/dances)   [dance]
   (sch/optional-key :class/starting) [participant]
   (sch/optional-key :class/rounds)   [round]})

(def activity-schema
  {:activity/id                        sch/Uuid
   :activity/position                  sch/Int
   :activity/name                      sch/Str
   :activity/number                    sch/Str
   :activity/comment                   sch/Str
   (sch/optional-key :activity/time)   sch/Inst
   (sch/optional-key :activity/source) round})

(def printer-options
  {:printer/preview              sch/Bool
   :printer/printer-select-paper sch/Bool})

(def presentation-options
  {:presentation/chinese-fonts sch/Bool
   :presentation/courier-font  sch/Str
   :presentation/arial-font    sch/Str})

(def dance-competition-options
  {:dance-competition/same-heat-all-dances           sch/Bool
   :dance-competition/heat-text-on-adjudicator-sheet sch/Bool
   :dance-competition/name-on-number-sign            sch/Bool
   :dance-competition/skip-adjudicator-letter        sch/Bool
   :dance-competition/adjudicator-order-final        sch/Bool
   :dance-competition/random-order-in-heats          sch/Bool
   :dance-competition/club-on-number-sign            sch/Bool
   :dance-competition/adjudicator-order-other        sch/Bool})

(def competition-data-schema
  {:competition/id                              sch/Uuid
   :competition/name                            sch/Str
   :competition/location                        sch/Str
   :competition/date                            sch/Inst
   :competition/options                         (merge printer-options presentation-options dance-competition-options)
   (sch/optional-key :competition/adjudicators) [adjudicator]
   (sch/optional-key :competition/activities)   [activity-schema]
   (sch/optional-key :competition/panels)       [adjudicator-panel]
   (sch/optional-key :competition/classes)      [class-schema]
   (sch/optional-key :competition/participants) [participant]

   ;; TODO - this should be consolidated in a better way
   (sch/optional-key :app/id)                   sch/Int
   })

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
