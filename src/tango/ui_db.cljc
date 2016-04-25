(ns tango.ui-db
  (:require [datascript.core :as d]))

(defn participant-index
  "Return map of index number -> id"
  [cmp]
  (reduce
   (fn [index participant]
     (assoc index (:participant/number participant) (:participant/id participant)))
   {}
   (mapcat :class/starting (:competition/classes cmp))))

(defn sanitize [cmp]
  (let [index (participant-index cmp)]
    (clojure.walk/postwalk
     (fn [form]
       (cond
         ;; replace participant-number with participant id in results
         (:result/participant-number form) (dissoc
                                            (assoc form :result/participant
                                                   {:participant/id
                                                    (get index
                                                         (:result/participant-number form))})
                                            :result/participant-number)

         ;; adjudicator id should be in map form for lookup ref.
         (:judging/adjudicator form) (assoc form :judging/adjudicator
                                            {:adjudicator/id (:judging/adjudicator form)})

         ;; remove nil values
         (map? form) (let [m (into {} (remove (comp nil? second) form))]
                       (when (seq m)
                         m))
         
         :else form))
     cmp)))

;; get all stuff
;; transform stuff with fn

(defn get-competitions [conn]
  (d/q '[:find [(pull ?e [:competition/name])]
         :where [?e :competition/name]]
       (d/db conn)))

(defn query
  ([conn q]
   (d/q q (d/db conn)))
  ([conn q params]
   (d/q q (d/db conn) params)))

(defn transform-competition [db update-fn]
  (let [tx-data (update-fn)]
    (d/transact! db [tx-data])))

(defn create-connection [schema]
  (d/create-conn schema))

(def schema {;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; UI Application
             :app/id {:db/unique :db.unique/identity}
             
             :app/selected-competition {:db/cardinality :db.cardinality/one
                                        :db/valueType :db.type/ref}

             :app/new-competition {:db/cardinality :db.cardinality/one
                                   :db/valueType :db.type/ref}

             :app/selected-activites {:db/cardinality :db.cardinality/many
                                      :db/valueType :db.type/ref}
             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Competition
             :competition/name {:db/unique :db.unique/identity}
             
             :competition/adjudicators {:db/cardinality :db.cardinality/many
                                        :db/valueType :db.type/ref}

             :competition/panels {:db/cardinality :db.cardinality/many
                                  :db/valueType :db.type/ref}

             :competition/options {:db/isComponent true
                                   :db/valueType :db.type/ref}

             :competition/activities {:db/cardinality :db.cardinality/many
                                      :db/valueType :db.type/ref}

             :competition/classes {:db/cardinality :db.cardinality/many
                                   :db/valueType :db.type/ref}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Adjudicator Panels
             :adjudicator-panel/adjudicators {:db/cardinality :db.cardinality/many
                                              :db/valueType :db.type/ref}

             :adjudicator-panel/id {:db/unique :db.unique/identity}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Dance
             :dance/name {:db/unique :db.unique/identity}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Adjudicator
             :adjudicator/id {:db/unique :db.unique/identity}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Participant
             :participant/id {:db/unique :db.unique/identity}
             
             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Activity

             :activity/id {:db/unique :db.unique/identity}

             :activity/source {:db/cardinality :db.cardinality/one
                               :db/valueType :db.type/ref}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Rounds
             :round/id {:db/unique :db.unique/identity}
             
             :round/panel {:db/cardinality :db.cardinality/one
                           :db/valueType :db.type/ref}

             :round/dances {:db/cardinality :db.cardinality/many
                            :db/valueType :db.type/ref}

             :round/results {:db/cardinality :db.cardinality/many
                             :db/valueType :db.type/ref}

             :round/starting {:db/cardinality :db.cardinality/many
                              :db/valueType :db.type/ref}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Result
             :result/participant {:db/cardinality :db.cardinality/one
                                  :db/valueType :db.type/ref}

             :result/judgings {:db/cardinality :db.cardinality/many
                               :db/valueType :db.type/ref}

             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Judgings
             :judging/adjudicator {:db/cardinality :db.cardinality/one
                                   :db/valueType :db.type/ref}

             :juding/marks {:db/cardinality :db.cardinality/many
                            :db/valueType :db.type/ref}

             :mark/x {:db/unique :db.unique/identity}
             ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; Class
             :class/id {:db/unique :db.unique/identity}

             :class/dances {:db/cardinality :db.cardinality/many
                            :db/valueType :db.type/ref}

             :class/rounds {:db/cardinality :db.cardinality/many
                            :db/valueType :db.type/ref}

             :class/adjudicator-panel {:db/cardinality :db.cardinality/one
                                       :db/valueType :db.type/ref}

             :class/remaining {:db/cardinality :db.cardinality/many
                               :db/valueType :db.type/ref}

             :class/starting {:db/cardinality :db.cardinality/many
                              :db/valueType :db.type/ref}
             })


;; (defn remove-nils
;;   "remove pairs of key-value that has nil value from a (possibly nested) map. 
;;   also transform map to nil if all of its value are nil" 
;;   [nm]
;;   (clojure.walk/postwalk 
;;    (fn [el]
;;      (if (map? el)
;;        (let [m (into {} (remove (comp nil? second) el))]
;;          (when (seq m)
;;            m))
;;        el))
;;    nm))



;http://stackoverflow.com/questions/11577601/clojure-nested-map-change-value#11578370
;; round participants from number to real id
;;  :round/results [{:result/participant-number 30 => :result/participant {:participant/id xx
;; DONE---------------

;; :result/judgings
;; ({:judging/adjudicator is the actual adj id but must be in correct form {:adjudicator/id xx

;; (defn sanitize [cmp]
;;   (-> cmp
;;       (remove-nils)))

;; (defn normalize-participant [index cmp]
;;   (clojure.walk/postwalk
;;    (fn [form]
;;      (if (:result/participant-number form)
;;        (dissoc
;;         (assoc form :result/participant {:participant/id (get index (:result/participant-number form))})
;;         :result/participant-number)
;;        form))
;;    cmp))

;; (defn judging-adjudicator-id-to-map [cmp]
;;   (clojure.walk/postwalk
;;    (fn [form]
;;      (if (:judging/adjudicator form)
;;        (assoc form :judging/adjudicator {:adjudicator/id (:judging/adjudicator form)})
;;        form))
;;    cmp))
