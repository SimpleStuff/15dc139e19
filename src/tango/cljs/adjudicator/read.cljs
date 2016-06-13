(ns tango.cljs.adjudicator.read
  (:require [om.next :as om]))

(defmulti read om/dispatch)

(defmethod read :app/selected-activities
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-activities [])
   :query true})

(defmethod read :app/selected-adjudicator
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-adjudicator nil)
   :query true})

(defmethod read :app/results
  [{:keys [state]} _ _]
  {:value (get @state :app/results #{})
   :query true})

(defmethod read :app/heat-page
  [{:keys [state]} _ _]
  {:value (get @state :app/heat-page 1)})

(defmethod read :app/heat-page-size
  [{:keys [state]} _ _]
  {:value (get @state :app/heat-page-size 2)})


;(defmethod read :app/selected-page
;  [{:keys [state]} _ _]
;  {:value (d/q '[:find ?page .
;                 :where [[:app/id 1] :selected-page ?page]]
;               (d/db state))})
;
;(defmethod read :app/status
;  [{:keys [state]} _ _]
;  {:value (d/q '[:find ?status .
;                 :where [[:app/id 1] :app/status ?status]]
;               (d/db state))})
;
;(defmethod read :app/online?
;  [{:keys [state]} _ _]
;  {:value (d/q '[:find ?online .
;                 :where [[:app/id 1] :app/online? ?online]]
;               (d/db state))})
;
;(defn log [m]
;  (.log js/console m))
;
;(defmethod read :app/selected-activity
;  [{:keys [state query ast]} _ _]
;  {:value (do
;            (if query
;              (d/q '[:find (pull ?a selector) .
;                     :in $ selector
;                     :where [[:app/id 1] :app/selected-activity ?a]]
;                   (d/db state) query)))
;   :query true})
;
;(defmethod read :app/selected-adjudicator
;  [{:keys [state query]} _ _]
;  {:value                                                   ;{:adjudicator/name "Bob"}
;   (do
;     ;(log "Read Adjudicator ")
;     ;(log query)
;     (if query
;       (let [q
;             (d/q '[:find (pull ?a selector) .
;                    :in $ selector
;                    :where [[:app/id 1] :app/selected-adjudicator ?a]]
;                  (d/db state) query)]
;         ;(log "Read Done")
;         q)))})
;
;(defn loading? [conn]
;  (if (= :loading
;         (d/q '[:find ?s .
;                :where [[:app/id 1] :app/status ?s]]
;              (d/db conn)))
;    true
;    false))
;
;(defmethod read :app/results
;  [{:keys [state query]} _ _]
;  (do
;    ;(log "Read results ")
;    ;(log query)
;    ;(if loading?)
;    ;{:query true}
;
;    ;; TODO - make sure that results are only for this round
;    {:value (do
;              ;(log "Read Results")
;              ;(log query)
;              (if query
;                (let [res
;                      (d/q '[                               ;:find [(pull ?r selector) ...]
;                             :find [(pull ?r selector) ...]
;                             :in $ selector
;                             :where
;                             [[:app/id 1] :app/results ?r]
;                             [[:app/id 1] :app/selected-activity ?a]
;                             [?a :activity/id ?id]
;                             [?r :result/activity ?ra]
;                             [?ra :activity/id ?id]
;                             ]
;                           (d/db state) query)]
;                  ;(log "//////////////////////////////////////////")
;                  ;(log res)
;                  res)))
;     ;:query true
;     }))
;

;
;(defmethod read :app/admin-mode
;  [{:keys [state]} _ _]
;  {:value (d/q '[:find ?a .
;                 :where [[:app/id 1] :app/admin-mode ?a]]
;               (d/db state))})




