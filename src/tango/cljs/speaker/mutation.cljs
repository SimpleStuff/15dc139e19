(ns tango.cljs.speaker.mutation
  (:require
    [om.next :as om]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

;(defmethod mutate 'app/status
;  [{:keys [state]} _ {:keys [status]}]
;  {:value  {:keys [:app/status]}
;   :action (fn []
;             (swap! local-id assoc :app/status status)
;             (d/transact! state [{:app/id 1 :app/status status}]))})