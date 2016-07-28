(ns tango.datomic-storage
  (:require
    [datomic.api :as ds]
    [taoensso.timbre :as log]
    [tango.ui-db :as ui]))

;; Provides useful Timbre aliases in this ns
(log/refer-timbre)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils
(defn clean-retraction-data [c]
  (clojure.walk/postwalk
    (fn [form]
      (cond
        ;; fix lookup ref
        (:participant/id form) [:participant/id (:participant/id form)]

        (:adjudicator-panel/id form) [:adjudicator-panel/id (:adjudicator-panel/id form)]

        (:dance/id form) [:dance/id (:dance/id form)]

        (:adjudicator/id form) [:adjudicator/id (:adjudicator/id form)]

        :else form))
    c))

(defn create-retraction [e-id attr val]
  [:db/retract e-id attr val])

;; TODO - looks more complicated than it should be..
(defn create-update-retractions [old new pre-diff-fn]
  (let [[to-retract-raw _ _] (clojure.data/diff (pre-diff-fn old) (pre-diff-fn new))
        to-retract (filter val to-retract-raw)
        filter-nil (fn [v] (vec (filter #(not (nil? %)) v)))
        e-id (:db/id old)]
    (apply concat
           (filter-nil
             (map clean-retraction-data
                  (for [stuff to-retract]
                    (when-not (= (key stuff) :db/id)
                      (if (or (vector? (val stuff)) (set? (val stuff)))
                        (filter-nil (map #(when % (create-retraction e-id (key stuff) %)) (val stuff)))
                        [(create-retraction e-id (key stuff) (val stuff))]))))))))

;; TODO fixa id so that we can have only one selected
(defn create-literal
  ([]
    (ds/tempid :db.part/user))
  ([id]
   (ds/tempid :db.part/user (- id 100000))))

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
                          #{}))
        :else form))
    data))

(defn delete-storage [uri]
  (ds/delete-database uri))

(defn create-storage [uri schema]
  (do
    (ds/create-database uri)
    @(ds/transact (ds/connect uri) schema)))

; dont make schema trans
(defn create-connection [uri]
  (ds/connect uri))

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
  @(ds/transact conn [(assoc (fix-id (clean-import-data tx)) :app/id 1)]))


(defn query-adjudicators [conn query]
  (ds/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :adjudicator/id]]
        (ds/db conn) query))

(defn query-adjudicator-panels [conn query]
  (ds/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :adjudicator-panel/id]]
        (ds/db conn) query))

(defn query-classes [conn query]
  (ds/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :class/id]]
        (ds/db conn) query))

(defn query-activities [conn query]
  (clean-data
    (ds/q '[:find [(pull ?e selector) ...]
           :in $ selector
           :where
           [?e :activity/id]]
          (ds/db conn) query)))

(defn query-participants [conn query]
  (clean-data
    (ds/q '[:find [(pull ?e selector) ...]
           :in $ selector
           :where
           [?e :participant/id]]
          (ds/db conn) query)))

(defn query-competition [conn query]
  (ds/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :competition/id]]
        (ds/db conn) query))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Rounds

(defn select-round [conn activity-id]
  @(ds/transact conn [{:app/selected-activities {:db/id [:activity/id activity-id]}
                      ;:app/id 1
                      :db/id                    [:app/id 1]                                ; (d/tempid :db.part/user)
                      }
                     ]))

(defn deselect-round [conn activity-id]
  @(ds/transact conn [[:db/retract [:app/id 1]
                      :app/selected-activities [:activity/id activity-id]]]))

(defn select-speaker-round [conn activity-id]
  @(ds/transact conn [{:app/speaker-activities {:db/id [:activity/id activity-id]}
                      :db/id                   [:app/id 1]}]))

(defn deselect-speaker-round [conn activity-id]
  @(ds/transact conn [[:db/retract [:app/id 1]
                      :app/speaker-activities [:activity/id activity-id]]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Results
(defn set-results [conn results]
  @(ds/transact conn (mapv fix-id results)))

(defn confirm-activity [conn confirmation]
  @(ds/transact conn (mapv fix-id confirmation)))

(defn query-confirmation [conn query]
  (ds/q '[:find [(pull ?e selector) ...]
         :in $ selector
         :where
         [?e :activity/confirmed-by]]
        (ds/db conn) query))

(defn transform-competition [db update-fn]
  (let [tx-data (update-fn)]
    @(ds/transact db [tx-data])))

(defn get-selected-activities [conn query]
  (do (log/info "DT query " query)
      (clean-data (ds/q '[:find [(pull ?a selector) ...]
                         :in $ selector
                         :where
                         ;[?e :app/id 1]
                         [?e :app/selected-activities ?a]]
                        (ds/db conn) query))))

(defn get-speaker-activities [conn query]
  (do (log/info "get Speaker activities"))
  (clean-data (ds/q '[:find [(pull ?a selector) ...]
                     :in $ selector
                     :where
                     ;[?e :app/id 1]
                     [?e :app/speaker-activities ?a]]
                    (ds/db conn) query)))

;; TODO - need to pull only for a specific activity
;; understand how to query for guid value
(defn query-results [conn query activity-id]
  (ds/q '[:find [(pull ?e selector) ...]
         ;:find (pull ?a [*])
         :in $ selector ?id
         :where
         [?e :result/id]
         [?e :result/activity ?a]
         [?a :activity/id ?id]]
        (ds/db conn) query activity-id))

(defn query-all-results [conn query]
  (ds/q '[:find [(pull ?e selector) ...]
         ;:find (pull ?a [*])
         :in $ selector
         :where
         [?e :result/id]]
        (ds/db conn) query))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client information
(defn set-client-information [conn client-info]
  @(ds/transact conn [(fix-id client-info)]))

(defn query-clients [conn query]
  (clean-data (ds/q '[:find [(pull ?c selector) ...]
                     :in $ selector
                     :where
                     [?c :client/id]]
                    (ds/db conn) query)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Competition
(defn create-competition [conn competition]
  @(ds/transact conn [(fix-id competition)]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Class
(defn- create-new-class [conn competition-id class]
  @(ds/transact conn [{:competition/classes (fix-id class)
                      :db/id                [:competition/id competition-id]}]))

(defn delete-class [conn competition-id class-id]
  @(ds/transact conn [[:db/retract [:competition/id competition-id]
                      :competition/classes [:class/id class-id]]]))


;http://augustl.com/blog/2013/ordering_cardinality_many_in_datomic/
;https://github.com/dwhjames/datomic-linklist
(defn transact-class [conn competition-id class]
  (let [existing (first (ds/q '[:find [(pull ?e [*
                                                {:class/starting [:participant/id]}
                                                {:class/adjudicator-panel [:adjudicator-panel/id]}
                                                {:class/dances [:dance/id]}])]
                               :in $ ?id
                               :where [?e :class/id ?id]]
                              (ds/db conn) (:class/id class)))
        sort-for-diff (fn [class]
                        (let [with-participants
                              (update-in class [:class/starting]
                                         #(set (map (fn [x] (select-keys x [:participant/id])) %)))]
                          (update-in with-participants [:class/dances]
                                     #(set (map (fn [x] (select-keys x [:dance/id])) %)))))
        retract-tx (create-update-retractions existing (merge existing class) sort-for-diff)]
    (log/info (str "Existing : " existing))
    (log/info (str "Update to : " class))
    (log/info (str "Retract tx : " (into [] retract-tx)))
    (if existing
      @(ds/transact conn (into retract-tx [(fix-id class)]))
      (create-new-class conn competition-id class))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Adjudicator Panels
(defn transact-adjudicator-panels [conn competition-id adjudicator-panel]
  (let [existing (first (ds/q '[:find [(pull ?e [* {:adjudicator-panel/adjudicators [:adjudicator/id]}])]
                                :in $ ?id
                                :where [?e :adjudicator-panel/id ?id]]
                              (ds/db conn) (:adjudicator-panel/id adjudicator-panel)))
        sort-for-diff (fn [panel]
                        (update-in panel [:adjudicator-panel/adjudicators]
                                   #(set (map (fn [x] (select-keys x [:adjudicator/id])) %))))
        retract-tx (create-update-retractions existing (merge existing adjudicator-panel) sort-for-diff)]
    (if existing
      @(ds/transact conn (into retract-tx [(fix-id adjudicator-panel)]))
      @(ds/transact conn [{:competition/panels (fix-id adjudicator-panel)
                           :db/id              [:competition/id competition-id]}]))))

;; delete
;; :db.fn/retractEntity
#_[[:db.fn/retractEntity id-of-jane]
 [:db.fn/retractEntity [:person/email "jdoe@example.com"]]]
(defn delete-adjudicator-panel [conn competition-id adjudicator-id]
  @(ds/transact conn [#_[:db/retract [:competition/id competition-id]
                       :competition/panels [:adjudicator-panel/id adjudicator-id]]
                      [:db.fn/retractEntity [:adjudicator-panel/id adjudicator-id]]
                      ]))