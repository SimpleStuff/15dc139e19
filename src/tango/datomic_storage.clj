(ns tango.datomic-storage
  (:require
    [datomic.api :as d]
    ))

(def schema-test
  [{:db/id                 #db/id[:db.part/db]
    :db/ident              :competition/name
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/fulltext           true
    :db/doc                "A competitions name"
    :db.install/_attribute :db.part/db}])

(defn create-connection [schema]
  (d/create-database "datomic:free://localhost:4334//competitions"))

(def uri "datomic:free://localhost:4334//competitions")

;; create db
(d/create-database uri)

;; create conn
(def conn (d/connect uri))

;; add schema
;;@(d/transact conn schema-test)

;; simple test data
;(def test-data [{:competition/name "Rikstävling i disco"
;                 :db/id #db/id[:db.part/user -100000]}])

;; transact data
;@(d/transact conn test-data)

;; query
(def result-1
  (d/q '[:find ?n :where [?n :competition/name]] (d/db conn)))

;; test entity api
(:competition/name (d/entity (d/db conn) (ffirst result-1)))

;; test of pull
(d/q '[:find [(pull ?n [:competition/name])]
       :where [?n :competition/name]]
     (d/db conn))