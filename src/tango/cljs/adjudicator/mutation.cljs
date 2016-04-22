(ns tango.cljs.adjudicator.mutation
  (:require
    [om.next :as om]
    [datascript.core :as d]))


(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/status
  [{:keys [state]} _ {:keys [status]}]
  {:value  {:keys [:app/status]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/status status}]))
   :remote true})

(defmethod mutate 'app/selected-activity-status
  [{:keys [state]} _ {:keys [status]}]
  {:value  {:keys []}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/selected-activity-status status}]))})

(defmethod mutate 'app/online?
  [{:keys [state]} _ {:keys [online?]}]
  {:value  {:keys [:app/online?]}
   :remote true
   :action (fn []
             (do
               ;(log "Mutate online")
               ;(log online?)
               (d/transact! state [{:app/id 1 :app/online? online?}])))})

(defmethod mutate 'app/select-activity
  [{:keys [state]} _ {:keys [activity]}]
  {:value  {:keys [:app/selected-activity]}
   :action (fn []
             (do
               ;(log "Mutate")
               (d/transact! state [{:app/id 1 :app/selected-activity activity}])))})

(defmethod mutate 'app/select-adjudicator
  [{:keys [state]} _ adjudicator]
  {:value {:keys [:app/selected-adjudicator]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/selected-adjudicator adjudicator}]))})


;; TODO - this might be redundant if results are filtered on selected
;; activity
(defn fix-result [results]
  (mapv #(hash-map
          :result/id          (:result/id %)
          :result/mark-x      (:result/mark-x %)                              ;(if (:result/mark-x %) (:result/mark-x %) false)
          :result/point       (if (:result/point %) (:result/point %) 0)
          :result/participant [:participant/id (:participant/id (:result/participant %))]
          :result/activity    [:activity/id (:activity/id (:result/activity %))]
          :result/adjudicator [:adjudicator/id (:adjudicator/id (:result/adjudicator %))])
        results))

;; TODO - fix that set result with [] actually clear db current value
(defmethod mutate 'app/set-results
  [{:keys [state]} _ {:keys [results]}]
  {:value  {:keys [:app/results]}
   :action (fn []
             ;(log (str "SET RESULTS "))
             ;(log results)
             ;(log (fix-result results))
             (d/transact! state [{:app/id 1 :app/results (fix-result results)}]))})

;{:result/id 1 :result/adjudicator 2 :result/participant 3 :result/mark-x}
(defmethod mutate 'participant/set-result
  [{:keys [state]} _ {:keys [result/mark-x participant/x] :as result}]
  {:value   {:keys [:app/results]}
   :action  (fn []
              ;(log result)
              (let [q
                    (d/transact! state
                                 [{:db/id              -1
                                   :result/id          (:result/id result)
                                   :result/mark-x      mark-x
                                   :result/point       (if (:result/point result) (:result/point result) 0)
                                   :result/participant [:participant/id (:result/participant result)]
                                   :result/activity    [:activity/id (:result/activity result)]
                                   :result/adjudicator [:adjudicator/id (:result/adjudicator result)]}
                                  {:app/id 1 :app/results -1}])]
                ))
   :command true})

(defmethod mutate 'app/heat-page
  [{:keys [state]} _ {:keys [page]}]
  {:value  {:keys [:app/heat-page]}
   :remote true
   :action (fn []
             (do
               ;(log "Mutate online")
               ;(log online?)
               (d/transact! state [{:app/id 1 :app/heat-page page}])))})
