(ns tango.ui-db
  (:require [datascript.core :as d]
            ))


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

(def conn (d/create-conn {:competition/classes {:db/cardinality :db.cardinality/many
                                                :db/valueType :db.type/ref}
                          :competition/options {:db/isComponent true
                                                :db/valueType :db.type/ref}

                          :competition/panels {:db/cardinality :db.cardinality/many
                                                :db/valueType :db.type/ref}

                          :adjudicator-panel/adjudicators {:db/cardinality :db.cardinality/many
                                                           :db/valueType :db.type/ref}

                          :competition/activities {:db/cardinality :db.cardinality/many
                                                   :db/valueType :db.type/ref}

                          :activity/source {:db/cardinality :db.cardinality/one
                                            :db/valueType :db.type/ref}

                          :round/dances  {:db/cardinality :db.cardinality/many
                                                   :db/valueType :db.type/ref}

                          ;:class/name {:db/unique :db.unique/identity}
                          }))
