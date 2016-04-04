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

