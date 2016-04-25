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

(defn log [m]
  (.log js/console m))

(defmethod mutate 'app/online?
  [{:keys [state]} _ {:keys [online?]}]
  {:value  {:keys [:app/online?]}
   :action (fn []
             (do
               (log "Mutate online")
               (log online?)
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
                    (log competition)
                    (d/transact! state [competition])))})