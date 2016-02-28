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
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro def- [item value]
  `(def ^{:private true} ~item ~value))

(defn- format-date [date]
  (tf/unparse (tf/formatter "yyyy-MM-dd") (tf/parse date)) )

(defn- b2i [b]
  (if (true? b) 1 0))

(defn- get-db [data]
  (let [conn (d/create-conn uidb/schema)]
    (d/transact! conn [(uidb/sanitize data)])
    (d/db conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DancePerfect
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- make-dance-perfect-node [content]
  (xml/element
    :DancePerfect
    {:Version "4.1"}
    content))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DancePerfect/CompData
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- make-comp-data-node [query-result]
  (let [result (first (first query-result))
        options (:competition/options result)]
    (xml/element
      :CompData
      {:Name               (:competition/name result)
       :Date               (format-date (:competition/date result))
       :Place              (:competition/location result)
       :Org                ""
       :AdjOrderFinal      (b2i (:dance-competition/adjudicator-order-final options))
       :AdjOrderOther      (b2i (:dance-competition/adjudicator-order-other options))
       :SameHeatAllDances  (b2i (:dance-competition/same-heat-all-dances options))
       :RadomHeats         (b2i (:dance-competition/random-order-in-heats options))
       :RandomHeats        (b2i (:dance-competition/random-order-in-heats options))
       :PreView            (b2i (:printer/preview options))
       :HeatText           (b2i (:dance-competition/heat-text-on-adjudicator-sheet options))
       :NameOnNumberSign   (b2i (:dance-competition/name-on-number-sign options))
       :ClubOnNumberSign   (b2i (:dance-competition/club-on-number-sign options))
       :SkipAdjLetter      (b2i (:dance-competition/skip-adjudicator-letter options))
       :PrinterSelectPaper (b2i (:printer/printer-select-paper options))
       :ChineseFonts       (b2i (:presentation/chinese-fonts options))
       :ArialFont          (:presentation/arial-font options)
       :CourierFont        (:presentation/courier-font options)
       }

      )))

(def- make-comp-data-node-query
  '[:find (pull ?e [:competition/name
                    :competition/date
                    :competition/location
                    :competition/options])
    :where [?e :competition/name]]
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DancePerfect/AdjPanelList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- make-adj-panel-list-node [content]
  (xml/element
    :AdjPanelList
    {}
    content))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DancePerfect/AdjPanelList/AdjList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- make-adjudicator-node [adjudicator-info]
  (let [name (:adjudicator/name adjudicator-info)
        id (:adjudicator/id adjudicator-info)
        country (:adjudicator/country adjudicator-info)]
    (xml/element :Adjudicator {:Seq (dec id)
                               :Name name
                               :Country country})
    ))

(defn- make-adj-list-node [query-result]
  (let [adjudicator-infos (map first query-result)]
    (xml/element
      :AdjList
      {:Qty (count adjudicator-infos)}
      (map make-adjudicator-node (sort-by :adjudicator/id adjudicator-infos)))
    ))

(def- make-adj-list-node-query
  '[:find (pull ?e [*])
    :where [?e :adjudicator/id]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DancePerfect/AdjPanelList/PanelList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- make-panel-adj-node [query-result seq-number]
  (xml/element
    :PanelAdj
    {:Seq seq-number
     :AdjNumber (:adjudicator/id query-result)}))

(defn- make-panel-node [panel seq-number]
  (let [adjudicator-infos (:adjudicator-panel/adjudicators panel)]
    (xml/element
      :Panel
      {:Seq seq-number
       :Qty (count adjudicator-infos)}
      (reduce
        (fn [state adj-info] (conj state (make-panel-adj-node adj-info (count state))))
        []
        adjudicator-infos))))

(defn- make-panel-list-node [qr]
  (let [adjudicator-panels (reduce conj [] (map first qr))
        n (- 30 (count adjudicator-panels))
        padded-panels (repeat n {:adjudicator-panel/adjudicators []})
        all-panels (into adjudicator-panels padded-panels)]
    (xml/element :PanelList
                 {:Qty (count all-panels)}
                 (reduce
                   (fn [elements panel]
                     (conj elements (make-panel-node panel (count elements))))
                   []
                   all-panels))))

(def- make-panel-list-node-query
  '[:find (pull ?e [{:adjudicator-panel/adjudicators [:adjudicator/id]}])
    :where [?e :adjudicator-panel/id]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Export Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def- export-definition
  {
   :xml-factory make-dance-perfect-node
   :content [{:xml-factory make-comp-data-node
              :query make-comp-data-node-query}
             {:xml-factory make-adj-panel-list-node
              :content [{:xml-factory make-adj-list-node
                         :query       make-adj-list-node-query}
                        {:xml-factory make-panel-list-node
                         :query       make-panel-list-node-query}
                        ]}
             ]})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Export
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- export-xml [db export-def]
  (let [xml-factory (:xml-factory export-def)
        query (:query export-def)
        content (:content export-def)
        export-xml-with-db (partial export-xml db)
        ]
    (if (nil? query)
      (xml-factory (map export-xml-with-db content))
      (xml-factory (d/q query db)))
    ))

(defn export [data export-def file]
  (let
    [db (get-db data)]
    (spit file (xml/emit-str (export-xml db export-def)))))

(export u/expected-small-example export-definition "/tmp/baz.xml")

