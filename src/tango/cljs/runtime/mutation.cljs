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

(defmethod mutate 'app/select-speaker-activity
  [{:keys [state]} _ {:keys [activity/id]}]
  {:value  {:keys []}
   :action (fn []
             (do
               (log "Pre Update")
               (let [activity
                     (first (filter #(= (:activity/id %) id)
                                    (:competition/activities (:app/selected-competition @state))))]
                 (swap! state (fn [current]
                                (update-in current [:app/speaker-activities] #(conj % activity)))))
               (log "Post Update")
               ;(log (:app/speaker-activites @state))
               ))
   :command true})

(defmethod mutate 'app/set-client-info
  [{:keys [state]} _ {:keys [client/name client/id client/user] :as client}]
  {:value   {:keys []}
   :action  (fn []
              (swap! state (fn [current]
                             (let [clean-clients (filter #(not= id (:client/id %)) (:app/clients current))]
                               (update-in current [:app/clients] #(conj clean-clients client))))))
   ;:action  (fn []
   ;           (swap! state (fn [current]
   ;                          (let [client (first
   ;                                         (filter #(= id (:client/id %)) (:app/clients current)))]
   ;                            )))
   ;           (swap! state (fn [current]
   ;                          (merge current {:app/local-id    id
   ;                                          :app/client-name name})))
   ;           )
   :command true})