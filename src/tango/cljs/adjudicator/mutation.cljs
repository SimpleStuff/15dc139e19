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
             (d/transact! state [{:app/id 1 :app/status status}]))})

(defmethod mutate 'app/online?
  [{:keys [state]} _ {:keys [online?]}]
  {:value  {:keys [:app/online?]}
   :action (fn []
             (do
               (log "Mutate online")
               (log online?)
               (d/transact! state [{:app/id 1 :app/online? online?}])))})

