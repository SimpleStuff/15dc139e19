(ns tango.datomic-storage
  (:require
    [datomic.api :as d]
    [taoensso.timbre :as log]
    [tango.ui-db :as ui]))

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
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :app/speaker-activites
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/many
    :db/doc                "The applications selected speaker activites"
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

   ;;;;;;;;; HAAAAAAAAAAAACKKK
   ;;:round/speaker-dances
   {:db/id                 #db/id[:db.part/db]
    :db/ident              :round/speaker-dances
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/many
    :db/doc                "The dances for this speaker round"
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
    :db/ident              :activity/position
    :db/valueType          :db.type/long
    :db/cardinality        :db.cardinality/one
    :db/doc                "An activitys position"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :activity/name
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/doc                "An activity name"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :activity/confirmed-by
    :db/valueType          :db.type/ref
    :db/cardinality        :db.cardinality/many
    :db/doc                "The adjudicators that have confirmed results for this activity"
    :db.install/_attribute :db.part/db}

   {:db/id                 #db/id[:db.part/db]
    :db/ident              :activity/number
    :db/valueType          :db.type/string
    :db/cardinality        :db.cardinality/one
    :db/doc                "An activitys number"
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
    :db/ident              :round/index
    :db/valueType          :db.type/long
    :db/cardinality        :db.cardinality/one
    :db/doc                "A rounds index in its class"
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

;; TODO - do not leak db/id?
(defn clean-data [data]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        ;; Competition should have id
        (:db/id form) (if (:db/ident form)
                        (:db/ident form)
                        (if (> (count (keys form)) 1)
                          (dissoc form :db/id)
                          #{}
                          ;form
                          ))

        :else form))
    data))

(defn delete-storage [uri]
  (d/delete-database uri))

(defn create-storage [uri schema]
  (do
    (d/create-database uri)
    @(d/transact (d/connect uri) schema)))

; dont make schema trans
(defn create-connection [uri]
  (d/connect uri))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn participant-index
  "Return map of index number -> id"
  [cmp]
  (reduce
    (fn [index participant]
      (assoc index (:participant/number participant) (:participant/id participant)))
    {}
    (mapcat :class/starting (:competition/classes cmp))))

(defn make-result [id participant-id point adjudicator-id mark-x]
  {:result/id          id
   :result/participant participant-id
   :result/point       point
   :result/adjudicator {:adjudicator/id adjudicator-id}
   :result/mark-x mark-x})

(defn transform-result [old-result number-to-id-index]
  (let [participant-id {:participant/id
                        (get number-to-id-index (:result/participant-number old-result))}
        old-judgings (:result/judgings old-result)]
    (mapv #(make-result
           (java.util.UUID/randomUUID)
           participant-id
           0
           (:judging/adjudicator %)
           (:mark/x (first (:judging/marks %))))
         old-judgings)))

(def status-convertion
  {:completed :status/completed
   :not-started :status/not-started})

(defn round-type-convertion [old-type]
  (keyword "round-type" (name old-type)))

(defn remove-nil-values [m]
  (let [new-m (into {} (remove (comp nil? second) m))]
    (when (seq new-m)
      new-m)))

(defn clean-import-data [import-data]
  (let [index (participant-index import-data)]
    (clojure.walk/postwalk
      (fn [form]
        (cond
          ;; replace participant-number with participant id in results
          (:round/id form)
          (apply
            dissoc
            (merge (remove-nil-values form)
                   {:round/results
                                            (vec (mapcat #(transform-result % index) (:round/results form)))
                    :round/number-of-heats  (:round/heats form)
                    :round/number-to-recall (:round/recall form)
                    :round/status           (get status-convertion (:round/status form))
                    :round/type             (round-type-convertion (:round/type form))
                    :round/number           (str (:round/number form))})
            [:round/class-id
             :round/heats
             :round/recall])

          ;; remove class/remaining, it should be derived
          (:class/remaining form) (dissoc form :class/remaining)

          ;; activity/numer should always be a string value
          (:activity/number form)
          (assoc (remove-nil-values form)
            :activity/number (str (:activity/number form)))

          ;; Competition should have id
          (:competition/name form) (assoc form :competition/id (java.util.UUID/randomUUID))

          :else form
          ))
      import-data)))

(defn transact-competition [conn tx]
  @(d/transact conn [(assoc (fix-id (clean-import-data tx)) :app/id 1)]))


(defn query-adjudicators [conn query]
  (d/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :adjudicator/id]]
       (d/db conn) query))

(defn query-adjudicator-panels [conn query]
  (d/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :adjudicator-panel/id]]
       (d/db conn) query))

(defn query-classes [conn query]
  (d/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :class/id]]
       (d/db conn) query))

(defn query-activities [conn query]
  (clean-data
    (d/q '[:find [(pull ?e selector) ...]
           :in $ selector
           :where
           [?e :activity/id]]
         (d/db conn) query)))

(defn query-participants [conn query]
  (clean-data
    (d/q '[:find [(pull ?e selector) ...]
           :in $ selector
           :where
           [?e :participant/id]]
         (d/db conn) query)))

(defn query-competition [conn query]
  (d/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :competition/id]]
       (d/db conn) query))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rounds

(defn select-round [conn activity-id]
  @(d/transact conn [{:app/selected-activities {:db/id [:activity/id activity-id]}
                      ;:app/id 1
                      :db/id                   [:app/id 1]                                ; (d/tempid :db.part/user)
                      }
                     ]))

(defn deselect-round [conn activity-id]
  @(d/transact conn [[:db/retract [:app/id 1]
                      :app/selected-activities [:activity/id activity-id]]]))

(defn select-speaker-round [conn activity-id]
  @(d/transact conn [{:app/speaker-activities {:db/id [:activity/id activity-id]}
                      :db/id                  [:app/id 1]}]))

(defn deselect-speaker-round [conn activity-id]
  @(d/transact conn [[:db/retract [:app/id 1]
                      :app/speaker-activities [:activity/id activity-id]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
(defn set-results [conn results]
  @(d/transact conn (mapv fix-id results)))

(defn confirm-activity [conn confirmation]
  @(d/transact conn (mapv fix-id confirmation)))

(defn query-confirmation [conn query]
  (d/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :activity/confirmed-by]]
       (d/db conn) query))

(defn transform-competition [db update-fn]
  (let [tx-data (update-fn)]
    @(d/transact db [tx-data])))

(defn get-selected-activities [conn query]
  (do (log/info "DT query " query)
      (clean-data (d/q '[:find [(pull ?a selector) ...]
                         :in $ selector
                         :where
                         ;[?e :app/id 1]
                         [?e :app/selected-activities ?a]]
                       (d/db conn) query))))

(defn get-speaker-activities [conn query]
  (do (log/info "get Speaker activities"))
  (clean-data (d/q '[:find [(pull ?a selector) ...]
                     :in $ selector
                     :where
                     ;[?e :app/id 1]
                     [?e :app/speaker-activities ?a]]
                   (d/db conn) query)))

;(defn get-speaker-activites [conn query]
;  (do (log/info "Selected Speaker Activites " query)
;      (d/q '[:find [(pull ?a selector) ...]
;             :in $ selector
;             :where
;             [?e :app/id 1]
;             [?e :app/speaker-activites ?a]]
;           (d/db conn) query)))

;(defn get-selected-activites [conn query]
;  (do (log/info "Selected Activites " query)
;      (d/q '[:find [(pull ?a selector) ...]
;             ;:find ?a
;             :in $ selector
;             :where
;             [?e :app/id 1]
;             [?e :app/selected-activites ?a]]
;           (d/db conn) query)))

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
;; Client information
(defn set-client-information [conn client-info]
  @(d/transact conn [(fix-id client-info)]))

(defn query-clients [conn query]
  (clean-data (d/q '[:find [(pull ?c selector) ...]
                     :in $ selector
                     :where
                     [?c :client/id]]
                   (d/db conn) query)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Competition
(defn create-competition [conn competition]
  @(d/transact conn [(fix-id competition)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Class
(defn create-class [conn competition-id class]
  @(d/transact conn [{:competition/classes (fix-id class)
                      :db/id               [:competition/id competition-id]}]))

(defn delete-class [conn competition-id class-id]
  @(d/transact conn [[:db/retract [:competition/id competition-id]
                      :competition/classes [:class/id class-id]]]))

;(d/q '[:find [(pull ?e selector) ...]
;       :in $ selector
;       :where
;       [?e :class/id]]
;     (d/db conn) query)

(defn clean-class [c]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        ;; fix lookup ref
        (:participant/id form) {:db/id [:participant/id (:participant/id form)]}

        :else form))
    c))

(defn do-stuff [class-id x y]
  (let [[to-retract to-add _] (clojure.data/diff x y)
        filter-nil (fn [v] (vec (filter #(not (nil? %)) v)))]
    (into (mapv (fn [v]
                  ;(when-not (= (key v) :db/id))
                  [:db/retract [:class/id class-id]
                   (key v)
                   (if (vector? (val v))
                     (filter-nil (val v))
                     (val v))]) (filter-nil to-retract))
          (mapv (fn [v]
                  (when-not (= (key v) :db/id)
                    [:db/add [:class/id class-id]
                     (key v)
                     (if (vector? (val v))
                       (filter-nil (val v))
                       (val v))])) (filter-nil to-add)))
    ))

(defn update-class [conn class]
  (let [existing (first (d/q '[:find [(pull ?e [* {:class/starting [:participant/id]}])]
                               :in $ ?id
                               :where [?e :class/id ?id]]
                             (d/db conn) (:class/id class)))
        tx (do-stuff (:class/id class) existing (clean-class class))]
    tx
    ;@(d/transact conn (fix-id tx))
    ))

;(defn deselect-round [conn activity-id]
;  @(d/transact conn [[:db/retract [:app/id 1]
;                      :app/selected-activities [:activity/id activity-id]]]))
