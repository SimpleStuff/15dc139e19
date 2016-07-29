(ns tango.export
  (:require [datascript.core :as d]
            [tango.ui-db :as uidb]
            [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
    ;[tango.expected.expected-small-result :as esr]
            [clojure.data.zip.xml :as zx]
            ))

(def schema {;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
             ;; UI Application
             :app/id {:db/unique :db.unique/identity}

             :app/selected-competition {:db/cardinality :db.cardinality/one
                                        :db/valueType :db.type/ref}

             :app/new-competition {:db/cardinality :db.cardinality/one
                                   :db/valueType :db.type/ref}

             :app/selected-activites {:db/cardinality :db.cardinality/many
                                      :db/valueType :db.type/ref}

             :app/results {:db/cardinality :db.cardinality/many
                           :db/valueType :db.type/ref}

             :app/confirmed {:db/cardinality :db.cardinality/many
                             :db/valueType :db.type/ref}

             :app/speaker-activites {:db/cardinality :db.cardinality/many
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

             :activity/confirmed-by {:db/cardinality :db.cardinality/many
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

             :result/id {:db/unique :db.unique/identity}
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


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defmacro def- [item value]
  `(def ^{:private true} ~item ~value))

(defn- format-date [date]
  (let [df (java.text.SimpleDateFormat. "yyyy-MM-dd")]
    (.format df date)))

(defn- b2i [b]
  (if (true? b) 1 0))

(defn- get-db [data]
  (let [conn (d/create-conn schema)]
    (d/transact! conn [(sanitize data)])
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
  (let [result (ffirst query-result)
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
    {:Seq       seq-number
     :AdjNumber (dec (:adjudicator/id query-result))}))

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
  (let [adjudicator-panels (vec (map first qr))
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
;; DancePerfect/ClassList
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- make-dance-node [dance seq]
  (xml/element
    :Dance
    {:Seq seq
     :Name (:dance/name dance)}))

(defn- make-dance-list-node [dances]
  (xml/element
    :DanceList
    {:Qty (count dances)}
    (reduce (fn [elements dance]
              (conj elements (make-dance-node dance (count elements))))
            [] dances)))

(defn- make-couple-node [participant seq]
  (xml/element
    :Couple
    {:Seq seq
     :Number (:participant/number participant)
     :Name (:participant/name participant)
     :Club (:participant/club participant)
     :License ""}))

(defn- make-start-list-node [participants]
  (xml/element
    :StartList
    {:Qty (count participants)}
    (reduce (fn [elements participant]
              (conj elements (make-couple-node participant (count elements))))
            [] participants)))

(defn- make-class-node [class seq-number]
  (let []
    (xml/element
      :Class
      {:Seq      seq-number
       :Name     (:class/name class)
       :AdjPanel (:adjudicator-panel/name (:class/adjudicator-panel class))}
      (make-dance-list-node (:class/dances class))
      (make-start-list-node (:class/starting class))
      )))

(defn- make-class-list-node [query-result]
  (let [classes (vec (map first query-result))]
    (xml/element :ClassList
                 {}
                 (reduce
                   (fn [elements class-node]
                     (conj elements (make-class-node class-node (count elements))))
                   []
                   classes))))

(def- make-class-list-node-query
      '[:find (pull ?e [:class/name
                        {:class/adjudicator-panel [:adjudicator-panel/name]}
                        {:class/dances [:dance/name]}
                        {:class/starting [:participant/club
                                          :participant/name
                                          :participant/number]}
                        ])
        :where [?e :class/id]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Export Definition
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def- export-definition
      {
       :xml-factory make-dance-perfect-node
       :content     [{:xml-factory make-comp-data-node
                      :query       make-comp-data-node-query}
                     {:xml-factory make-adj-panel-list-node
                      :content     [{:xml-factory make-adj-list-node
                                     :query       make-adj-list-node-query}
                                    {:xml-factory make-panel-list-node
                                     :query       make-panel-list-node-query}
                                    ]}
                     {:xml-factory make-class-list-node
                      :query       make-class-list-node-query}
                     ]
       })

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Export
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- export-xml [db export-def]
  (let [xml-factory (:xml-factory export-def)
       query (:query export-def)
       content (:content export-def)
       export-xml-with-db (partial export-xml db)]
    (if (nil? query)
      (xml-factory (map export-xml-with-db content))
      (xml-factory (d/q query db)))))

(defn export [data]
  (let
    [db (get-db data)]
    (xml/emit-str (export-xml db export-definition))))

;(defn foo [x]
;  (+ 6 4))
;
;(defn bar [y]
;  (* 9 y))
;
;(export esr/expected-small-example)
;(bar 66)
