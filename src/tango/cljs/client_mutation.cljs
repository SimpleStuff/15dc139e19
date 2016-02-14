(ns tango.cljs.client-mutation
  (:require
    [om.next :as om]
    [datascript.core :as d]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/add-competition
  [{:keys [state]} _ params]
  {:value  {:keys [:app/competitions]}
   :action (fn []
             (when params
               (if (:competitions params)
                 (d/transact! state (:competitions params))
                 (d/transact! state [params]))))})

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

(defmethod mutate 'app/status
  [{:keys [state]} _ {:keys [status]}]
  {:value  {:keys [:app/status]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/status status}]))})

(defmethod mutate 'app/select-competition
  [{:keys [state]} _ {:keys [name]}]
  {:value  {:keys [:app/selected-competition]}
   :action (fn []
             (d/transact! state [{:app/id 1 :app/selected-competition {:competition/name name}}]))})