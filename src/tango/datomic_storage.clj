(ns tango.datomic-storage
  (:require
    [datomic.api :as d]
    [datascript.core :as ds]
    [taoensso.timbre :as log]
    [tango.ui-db :as ui]
    ))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;; TODO - move schema to its own file (liters start to cry otherwise)
(def application-schema
  [{:db/id                 #db/id[:db.part/db]
    :db/ident              :app/id
    :db/valueType          :db.type/long
    :db/unique             :db.unique/identity
    :db/cardinality        :db.cardinality/one
    :db/doc                "The applications id"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :app/selected-activity
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/one
    :db/doc                "The applications selected activity"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :app/selected-activites
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/many
    :db/doc                "The applications selected activites"
    :db.install/_attribute :db.part/db}])

(def result-schema
  [{:db/id                 #db/id[:db.part/db]
    :db/ident              :result/id
    :db/valueType          :db.type/uuid
    :db/unique             :db.unique/identity
    :db/cardinality        :db.cardinality/one
    :db/doc                "The results id"
    :db.install/_attribute :db.part/db}

   ;; mark-x
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :result/mark-x
    :db/valueType          :db.type/boolean
    :db/cardinality        :db.cardinality/one
    :db/doc                "Determines if a X has been marked in this result"
    :db.install/_attribute :db.part/db}

   ;; point
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :result/point
    :db/valueType          :db.type/long
    :db/cardinality        :db.cardinality/one
    :db/doc                "The number of points given a participant (can be neg.)"
    :db.install/_attribute :db.part/db}

   ;; result/participant
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :result/participant
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/one
    :db/doc                "The participant these result is associated with"
    :db.install/_attribute :db.part/db}

   ;; result/activity
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :result/activity
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/one
    :db/doc                "The activity these result is associated with"
    :db.install/_attribute :db.part/db}

   ;; result/adjudicator
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :result/adjudicator
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/one
    :db/doc                "The adjudicator these result is associated with"
    :db.install/_attribute :db.part/db}
   ])

(def select-activity-schema
  [;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Activity

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :activity/id
    :db/unique             :db.unique/identity
    :db/valueType          :db.type/uuid
    :db/cardinality        :db.cardinality/one
    :db/doc                "An activity id"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :activity/name
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/doc                "An activity name"
    :db.install/_attribute :db.part/db}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Round

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :round/recall
    :db/valueType          :db.type/long
    :db/cardinality        :db.cardinality/one
    :db/doc                "The number of participants to be recalled from a round"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :round/name
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/doc                "A rounds name"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :round/heats
    :db/valueType          :db.type/long
    :db/cardinality        :db.cardinality/one
    :db/doc                "A rounds number of heats"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :round/starting
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/many
    :db/doc                "The starting participants in a round"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :round/panel
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/one
    :db/doc                "The adjudicator panel in a round"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :round/dances
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/many
    :db/doc                "The dances in a round"
    :db.install/_attribute :db.part/db}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Dances
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :dance/name
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/doc                "A dance name"
    :db.install/_attribute :db.part/db}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Participant

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :participant/number
    :db/valueType          :db.type/long
    :db/cardinality        :db.cardinality/one
    :db/doc                "A participants start number"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :participant/id
    :db/unique             :db.unique/identity
    :db/valueType          :db.type/uuid
    :db/cardinality        :db.cardinality/one
    :db/doc                "A participants id"
    :db.install/_attribute :db.part/db}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Adjudicator Panel
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :adjudicator-panel/id
    :db/unique             :db.unique/identity
    :db/valueType          :db.type/uuid
    :db/cardinality        :db.cardinality/one
    :db/doc                "An adjudicator panels id"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :adjudicator-panel/name
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/doc                "An adjudicator panels name"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :adjudicator-panel/adjudicators
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/many
    :db/doc                "The adjudicators in a panel"
    :db.install/_attribute :db.part/db}

   ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
   ;; Adjudicator
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :adjudicator/id
    :db/unique             :db.unique/identity
    :db/valueType          :db.type/uuid
    :db/cardinality        :db.cardinality/one
    :db/doc                "An adjudicators id"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :adjudicator/name
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/doc                "An adjudicators name"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :adjudicator/number
    :db/valueType          :db.type/long
    :db/cardinality        :db.cardinality/one
    :db/doc                "An adjudicators number"
    :db.install/_attribute :db.part/db}
   ])


;; TODO fixa id so that we can have only one selected
(defn create-literal
  ([]
    (d/tempid :db.part/user))
  ([id]
   (d/tempid :db.part/user (- id 100000))))

(defn fix-id [round-data]
  (clojure.walk/postwalk
    (fn [form]
      (if (map? form) (assoc form :db/id (create-literal)) form))
    round-data))

(defn delete-storage [uri]
  (d/delete-database uri))

(defn create-storage [uri schema]
  (do
    (d/create-database uri)
    @(d/transact (d/connect uri) schema)))

; dont make schema trans
(defn create-connection [uri]
  (d/connect uri))

(defn select-round [conn round]
  @(d/transact conn [(fix-id {:app/selected-activites round
                              :app/id                1})]))

(defn set-results [conn results]
  @(d/transact conn (mapv fix-id results)))

(defn transform-competition [db update-fn]
  (let [tx-data (update-fn)]
    @(d/transact db [tx-data])))

(defn get-selected-activity [conn query]
  (do (log/info "DT query " query)
      (d/q '[:find (pull ?a selector) .
             :in $ selector
             :where
             [?e :app/id 1]
             [?e :app/selected-activity ?a]]
           (d/db conn) query)))

(defn get-selected-activites [conn query]
  (do (log/info "Selected Activites " query)
      (d/q '[:find [(pull ?a selector) ...]
             ;:find ?a
             :in $ selector
             :where
             [?e :app/id 1]
             [?e :app/selected-activites ?a]]
           (d/db conn) query)))

;; TODO - need to pull only for a specific activity
;; understand how to query for guid value
(defn query-results [conn query activity-id]
  (d/q '[:find [(pull ?e selector) ...]
         ;:find (pull ?a [*])
         :in $ selector ?id
         :where
         [?e :result/id]
         [?e :result/activity ?a]
         [?a :activity/id ?id]]
       (d/db conn) query activity-id))

(defn query-all-results [conn query]
  (d/q '[:find [(pull ?e selector) ...]
         ;:find (pull ?a [*])
         :in $ selector
         :where
         [?e :result/id]]
       (d/db conn) query))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples

;(def uri "datomic:mem://localhost:4334//competitions")
;
;(d/delete-database uri)
;;; create db
;(d/create-database uri)
;
;;; create conn
;(def conn (d/connect uri))
;
;;; add schema
;@(d/transact conn schema-test)
;
;;; simple test data
;(def test-data [{:competition/name "Rikstävling i disco"
;                 :db/id #db/id[:db.part/user -100000]}])
;
;(def test-no-id [{:competition/name "No ID"}])
;
;;; transact data
;@(d/transact conn test-data)
;
;;@(d/transact conn test-no-id)
;
;;; query
;(def result-1
;  (d/q '[:find ?n :where [?n :competition/name]] (d/db conn)))
;
;;; test entity api
;(:competition/name (d/entity (d/db conn) (ffirst result-1)))
;
;;; test of pull
;(d/q '[:find [(pull ?n [:competition/name])]
;       :where [?n :competition/name]]
;     (d/db conn))
;
;;; DS test
;(def ds-conn (ds/create-conn ui/schema))
;
;(ds/transact! ds-conn test-no-id)
;
;(ds/transact! ds-conn [(ui/sanitize u/expected-small-example)])
;
;ds-conn
;
;(ds/q '[:find (pull ?e [*])
;        :where [?e :competition/name]]
;      (ds/db ds-conn))
;
;(defn create-literal
;  ([]
;    (d/tempid :db.part/user))
;  ([id]
;   (d/tempid :db.part/user (- id 100000))))
;
;(create-literal 1)
;
;(d/transact conn [{:competition/name "Test"
;                   :db/id            (create-literal 1)}])
;
;(d/transact conn [[:db/add (create-literal 1) :competition/name "Ost"]])
;
;(def test-data (keys (ds/transact! ds-conn [(ui/sanitize u/expected-small-example)])))
;
;(ds/datoms (ds/db ds-conn) :eavt)
;
;(defn stuff [conn]
;  (let [dvec #(vector (:e %) (:a %) (:v %))]
;    (map dvec (ds/datoms (ds/db conn) :eavt))))
;
;(stuff ds-conn)