(ns tango.ui-db
  (:require [datascript.core :as d]
            ))

(defn remove-nils
  "remove pairs of key-value that has nil value from a (possibly nested) map. 
  also transform map to nil if all of its value are nil" 
  [nm]
  (clojure.walk/postwalk 
   (fn [el]
     (if (map? el)
       (let [m (into {} (remove (comp nil? second) el))]
         (when (seq m)
           m))
       el))
   nm))

(defn sanitize [cmp]
  (-> cmp
      (remove-nils)))

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
