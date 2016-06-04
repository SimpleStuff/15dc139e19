(ns tango.cljs.runtime.mutation
  (:require
    [om.next :as om]))

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/set-status
  [{:keys [state]} _ {:keys [status]}]
  {:value  {:keys [:app/status]}
   :action (fn []
             (swap! state assoc :app/status status))})

(defmethod mutate 'app/select-page
  [{:keys [state]} _ {:keys [selected-page]}]
  {:value  {:keys [:app/selected-page]}
   :action (fn []
             (swap! state assoc :app/selected-page selected-page))})

(defmethod mutate 'app/select-activity
  [{:keys [state]} _ {:keys [activity/id]}]
  {:value  {:keys []}
   :action (fn []
             (let [activity
                   (first (filter #(= (:activity/id %) id)
                                  (:competition/activities (:app/selected-competition @state))))]
               (swap! state (fn [current]
                              (update-in current [:app/selected-activities] #(conj % activity))))))
   :command true})