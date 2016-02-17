(ns tango.export
  (:require [datascript.core :as d]
            [tango.test-utils :as u]
            [tango.ui-db :as uidb]
            [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjPanelList/AdjList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- old-make-adjudicator-node [adjudicator-info]
  (let [name (:adjudicator/name adjudicator-info)
        id (:adjudicator/id adjudicator-info)
        country (:adjudicator/country adjudicator-info)]
    (xml/element :Adjudicator {:Seq (dec id)
                               :Name name
                               :Country country})
    ))

(defn- old-make-adj-list-node [query-result]
  (let [adjudicator-infos (map first query-result)]
    (xml/element
      :AdjList
      {:Qty (count adjudicator-infos)}
      (map old-make-adjudicator-node (sort-by :adjudicator/id adjudicator-infos)))
    ))

(defn- old-export-adj-list [db]
  (let [query-result (d/q '[:find (pull ?id [*])
                            :where
                            [?id :adjudicator/id]] db)]
    (old-make-adj-list-node query-result)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjPanelList/PanelList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- old-make-panel-adj-node [query-result seq-number]
  (xml/element
    :PanelAdj
    {:Seq seq-number
     :AdjNumber (:adjudicator/id query-result)}))

(defn- old-make-panel-node [panel seq-number]
  (let [adjudicator-infos (:adjudicator-panel/adjudicators panel)]
    (xml/element
      :Panel
      {:Seq seq-number
       :Qty (count adjudicator-infos)}
      (reduce
        (fn [state adj-info] (conj state (old-make-panel-adj-node adj-info (count state))))
        []
        adjudicator-infos))))

(defn- old-make-panel-list-node [adjudicator-panels]
  (xml/element
    :PanelList
    {:Qty (count adjudicator-panels)}
    (reduce (fn [elements panel]
              (conj elements (old-make-panel-node panel (count elements))))
            []
            adjudicator-panels)
  ))

(defn- old-export-panel-list [db]
  (let [qr (d/q '[:find (pull ?id [{:adjudicator-panel/adjudicators
                                                            [:adjudicator/id]}])
                                          :where
                                          [?id :adjudicator-panel/id]] db)
        adjudicator-panels (reduce conj [] (map first qr))
        n (- 30 (count adjudicator-panels))
        padded-panels (repeat n {:adjudicator-panel/adjudicators []})
        all-panels (into adjudicator-panels padded-panels)]
    (old-make-panel-list-node all-panels)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjPanelList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- old-export-adj-panel-list [db]
  (xml/element
    :AdjPanelList
    {}
    (old-export-adj-list db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- get-db [data]
  (let [conn (d/create-conn uidb/schema)]
    (d/transact! conn [(uidb/sanitize data)])
    (d/db conn)))

(defn- old-export-dance-perfect [db]
  (xml/element
      :DancePerfect
      {:Version "4.1"}
      [(old-export-adj-panel-list db)
       (old-export-panel-list db)]
      ))

(defn old-export [data]
  (let [db (get-db data)]
    (old-export-dance-perfect db) ;; TODO: def queries in file and pass in query result
    ))

(xml/indent-str
  (old-export u/expected-small-example))

