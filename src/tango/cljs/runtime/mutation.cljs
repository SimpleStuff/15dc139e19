(ns tango.cljs.runtime.mutation
  (:require
    [om.next :as om]))

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