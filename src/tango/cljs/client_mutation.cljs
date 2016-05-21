(ns tango.cljs.client-mutation
  (:require
    [om.next :as om]
    [datascript.core :as d]))



(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/add-competition
  [{:keys [state]} _ params]
  {:value  {:keys [:app/competitions]}
   :action (fn []
             (log "Addddding competttttion")
             (when params
               (if (:competitions params)
                 (do
                   (log "Pre tx")
                   (let [tx
                         (d/transact! state (:competitions params))]
                     (log "Post tx")
                     (log tx)))
                 (do
                   (log "Pre tx")
                   (let [tx
                         (d/transact! state [params])]
                     (log "Post tx")
                     (log tx))))))})

(defmethod mutate 'app/select-page
  [{:keys [state]} _ {:keys [page]}]
  {:value  {:keys [:app/selected-page]}
   :action (fn []
             (d/transact! state [{:app/id 1 :selected-page page}]))})

(defmethod mutate 'app/set-import-status
  [{:keys [state]} _ {:keys [status]}]
  {:value  {:keys [:app/import-status]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/import-status status}]))})

(defmethod mutate 'app/set-export-status
  [{:keys [state]} _ {:keys [status]}]
  (merge
    {:value   {:keys [:app/import-status]}
     :action  (fn []
                (d/transact! state [{:app/id 1 :app/export-status status}]))
     ;(when (= status :requested))
     :command true}))

(defmethod mutate 'app/status
  [{:keys [state]} _ {:keys [status]}]
  {:value  {:keys [:app/status]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/status status}]))})


(defmethod mutate 'app/online?
  [{:keys [state]} _ {:keys [online?]}]
  {:value  {:keys [:app/online?]}
   :action (fn []
             (do
               ;(log "Mutate online")
               ;(log online?)
               (d/transact! state [{:app/id 1 :app/online? online?}])))})

(defmethod mutate 'app/select-competition
  [{:keys [state]} _ {:keys [name]}]
  {:value  {:keys [:app/selected-competition]}
   :action (fn []
             (let [q
                   (d/transact! state [{:app/id 1 :app/selected-competition {:competition/name name}}])]
               q))})

(defmethod mutate 'app/select-activity
  [{:keys [state]} _ activity]
  {:value   {:keys [:app/selected-activity]}
   :command true
   :action  (fn []
              (let [tx
                    (d/transact! state [{:app/id 1 :app/selected-activites {:activity/id (:activity/id activity)}}])]
                ;(log tx)
                tx))})

;(d/transact! state [[:db/add 3 :competition/name "test 2"]])

(defmethod mutate 'app/update-competition
  [{:keys [state]} _ {:keys [db/id attribute value]}]
  {:value  {:keys [:app/selected-competition]}
   :action (fn []
             (let [q
                   (d/transact! state [[:db/add id attribute value]])]
               q))})

(defmethod mutate 'app/create-competition
  [{:keys [state]} _ {:keys [competition/name] :as competition}]
  {:value  {:keys []}
   :action (fn [] (do
                    ;(log competition)
                    (d/transact! state [competition])))})

(defn fix-result [results]
  (mapv #(hash-map
          :result/id (:result/id %)
          :result/mark-x (:result/mark-x %)                 ;(if (:result/mark-x %) (:result/mark-x %) false)
          :result/point       (if (:result/point %) (:result/point %) 0)
          ;:result/participant [:participant/id (:participant/id (:result/participant %))]
          :result/activity    [:activity/id (:activity/id (:result/activity %))]
          :result/adjudicator [:adjudicator/id (:adjudicator/id (:result/adjudicator %))]
          )
        results))

(defmethod mutate 'app/set-results
  [{:keys [state]} _ {:keys [results]}]
  {:value  {:keys [:app/results]}
   :action (fn []
             ;(log (str "SET RESULTS "))
             ;(log results)
             ;(log (fix-result results))
             (let [q
                   (d/transact! state [{:app/id 1 :app/results (fix-result results)}])]
               ;(log "Transaction Complete")
               ;(log q)
               q))})

(defn fix-confirmed [confirmed]
  (mapv #(hash-map
          :activity/id (:activity/id %)
          :activity/confirmed-by (mapv (fn [adj] [:adjudicator/id
                                                  (:adjudicator/id adj)])
                                       (:activity/confirmed-by %)))
        confirmed))

(defmethod mutate 'app/confirm
  [{:keys [state]} _ {:keys [confirmations]}]
  {:value  {:keys [:app/confirmed]}
   :action (fn []
             ;(log (str "SET CONFIRMATIONS "))
             ;(log results)
             ;(log (fix-confirmed confirmations))
             ;(log confirmations)
             (let [is-ok (:activity/id (first confirmations))
                   q
                   (when is-ok
                     (d/transact! state [{:app/id 1 :app/confirmed (fix-confirmed confirmations)}]))]
               ;(log "Transaction Complete")
               ;(log q)
               q))})

(defmethod mutate 'app/set-speaker-activity
  [{:keys [state]} _ activity]
  {:value   {:keys [:app/speaker-activites]}
   :action  (fn []
              ;(log "Set Speaker activity")
              ;(log activity)
              ;(log "Start Transaction")
              (d/transact! state [{:app/id 1 :app/speaker-activites {:activity/id (:activity/id activity)}}])
              ;(log "End Transaction")
              )
   :command true})