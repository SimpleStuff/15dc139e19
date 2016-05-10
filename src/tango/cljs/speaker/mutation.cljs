(ns tango.cljs.speaker.mutation
  (:require
    [om.next :as om]))

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/set-filter
  [{:keys [state]} _ {:keys [filter]}]
  {:value  {:keys [:app/filter]}
   :action (fn []
             (swap! state assoc :app/filter filter))})

;{:app/speaker-activites (into (:app/speaker-activites @app-state)
;                              new-acts)}

(defmethod mutate 'app/mark-activity
  [{:keys [state]} _ {:keys [activity/number]}]
  {:value  {:keys [:app/marked-activites
                   ;:app/speaker-activites
                   ]}
   :action (fn []
             (do
               ;(log @state)
               ;(swap! state (fn [old arg]
               ;               (merge @state
               ;                      {:app/marked-activites
               ;                       (set (conj (:app/marked-activites old) arg))}) number))
               (swap! state assoc :app/marked-activites
                      (conj (:app/marked-activites @state) number))
               (log "OK")
               ;(log @state)
               ))})

