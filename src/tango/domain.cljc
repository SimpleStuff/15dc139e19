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