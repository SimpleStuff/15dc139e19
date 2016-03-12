(ns tango.datomic-storage
  (:require
    [datomic.api :as d]
    [datascript.core :as ds]
    [tango.ui-db :as ui]
    [datascript.core :as ds]
    [tango.test-utils :as u]
    [datascript.core :as d]))

(def schema-test
  [{:db/id                 #db/id[:db.part/db]
    :db/ident              :competition/name
    :db/unique             :db.unique/identity
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/fulltext           true
    :db/doc                "A competitions name"
    :db.install/_attribute :db.part/db}])

(defn create-storage [uri]
  (d/create-database uri))

(defn create-connection [uri]
  (let [conn (d/connect uri)]
    @(d/transact conn schema-test)
    conn))

(defn transform-competition [db update-fn]
  (let [tx-data (update-fn)]
    @(d/transact db [tx-data])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Examples

(def uri "datomic:mem://localhost:4334//competitions")

(d/delete-database uri)
;; create db
(d/create-database uri)

;; create conn
(def conn (d/connect uri))

;; add schema
@(d/transact conn schema-test)

;; simple test data
(def test-data [{:competition/name "Rikstävling i disco"
                 :db/id #db/id[:db.part/user -100000]}])

(def test-no-id [{:competition/name "No ID"}])

;; transact data
@(d/transact conn test-data)

@(d/transact conn test-no-id)

;; query
(def result-1
  (d/q '[:find ?n :where [?n :competition/name]] (d/db conn)))

;; test entity api
(:competition/name (d/entity (d/db conn) (ffirst result-1)))

;; test of pull
(d/q '[:find [(pull ?n [:competition/name])]
       :where [?n :competition/name]]
     (d/db conn))

;; DS test
(def ds-conn (ds/create-conn ui/schema))

(ds/transact! ds-conn test-no-id)

(ds/transact! ds-conn [(ui/sanitize u/expected-small-example)])

ds-conn

(ds/q '[:find (pull ?e [*])
        :where [?e :competition/name]]
      (ds/db ds-conn))

(defn create-literal [id]
  (d/tempid :db.part/user (- id 100000)))

(create-literal 1)

(d/transact conn [{:competition/name "Test"
                   :db/id (create-literal 1)}])