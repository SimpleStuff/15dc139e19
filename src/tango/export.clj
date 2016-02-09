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
(defn make-adjudicator-node [adjudicator-infos]
  (let [name (:adjudicator/name adjudicator-infos)
        id (:adjudicator/id adjudicator-infos)
        country (:adjudicator/country adjudicator-infos)]
    (xml/element :Adjudicator {:Seq (dec id)
                               :Name name
                               :Country country})
    ))

(defn make-adj-list-node [query-result]
  (let [adjudicator-infos (map first query-result)]
    (xml/element
      :AdjList
      {:Qty (count adjudicator-infos)}
      (map make-adjudicator-node (sort-by :adjudicator/id adjudicator-infos)))
    ))

(defn export-adj-list [db]
  (let [query-result (d/q '[:find (pull ?id [*])
                            :where
                            [?id :adjudicator/id]] db)]
    (make-adj-list-node query-result)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjPanelList/PanelList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn make-panel-adj-node [query-result seq-number]
  (xml/element
    :PanelAdj
    {:Seq seq-number
     :AdjNumber (:adjudicator/id query-result)}))

(defn make-panel-node [panel seq-number]
  (let [adjudicator-infos (:adjudicator-panel/adjudicators panel)]
    (xml/element
      :Panel
      {:Seq seq-number
       :Qty (count adjudicator-infos)}
      (reduce
        (fn [state adj-info] (conj state (make-panel-adj-node adj-info (count state))))
        []
        adjudicator-infos))))

(defn make-panel-list-node [adjudicator-panels]
  (xml/element
    :PanelList
    {:Qty (count adjudicator-panels)}
    (reduce (fn [elements panel]
              (conj elements (make-panel-node panel (count elements))))
            []
            adjudicator-panels)
  ))

(defn export-panel-list [db]
  (let [qr (d/q '[:find (pull ?id [{:adjudicator-panel/adjudicators
                                                            [:adjudicator/id]}])
                                          :where
                                          [?id :adjudicator-panel/id]] db)
        adjudicator-panels (reduce conj [] (map first qr))
        n (- 30 (count adjudicator-panels))
        padded-panels (repeat n {:adjudicator-panel/adjudicators []})
        all-panels (reduce conj adjudicator-panels padded-panels)]
    (make-panel-list-node all-panels)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjPanelList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn export-adj-panel-list [db]
  (xml/element
    :AdjPanelList
    {}
    (export-adj-list db)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-db [data]
  (let [clean-data (uidb/sanitize data)
        conn (d/create-conn uidb/schema)]
    (d/transact! conn [clean-data])
    (d/db conn)))

(defn export-dance-perfect [db]
  (xml/element
      :DancePerfect
      {:Version "4.1"}
      [(export-adj-panel-list db)
       (export-panel-list db)]
      ))

(defn export [data]
  (let [db (get-db data)]
    (export-dance-perfect db)
    ))

(xml/indent-str
  (export u/expected-small-example))

