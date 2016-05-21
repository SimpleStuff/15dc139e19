(ns tango.cljs.runtime.mutation
  (:require
    [om.next :as om]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

;(defmethod mutate 'app/set-filter
;  [{:keys [state]} _ {:keys [filter]}]
;  {:value  {:keys [:app/filter]}
;   :action (fn []
;             (swap! state assoc :app/filter filter))})