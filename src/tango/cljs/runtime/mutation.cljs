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
   :command true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Classes
(defmethod mutate 'class/save
  [{:keys [state ast]} _ {:keys [class/name class/id] :as params}]
  {:value   {:keys []}
   :action  (fn []
              ;(swap! state (fn [current]
              ;               (update-in current
              ;                          [:app/selected-competition :competition/classes]
              ;                          (fn [current-classes]
              ;                            (conj current-classes {:class/name name
              ;                                                   :class/id   id})))))
              )
   :command (assoc ast :params
                       (merge params
                              {:competition/id
                               (:competition/id (:app/selected-competition @state))}))
   })

(defmethod mutate 'class/create
  [{:keys [state ast]} _ {:keys [class/name class/id] :as params}]
  {:value   {:keys []}
   :action  (fn []
              (swap! state (fn [current]
                             (update-in current
                                        [:app/selected-competition :competition/classes]
                                        (fn [current-classes]
                                          (conj current-classes {:class/name name
                                                                 :class/id   id}))))))
   ;:command (assoc ast :params
   ;                    (merge params
   ;                           {:competition/id
   ;                            (:competition/id (:app/selected-competition @state))}))
   })

(defmethod mutate 'class/delete
  [{:keys [state ast]} _ {:keys [class/id] :as params}]
  {:value   {:keys []}
   :action  (fn []
              (swap! state (fn [current]
                             (update-in current
                                        [:app/selected-competition :competition/classes]
                                        (fn [current-classes]
                                          (filter #(not= (:class/id %) id) current-classes))))))
   :command (assoc ast :params
                       (merge params
                              {:competition/id
                               (:competition/id (:app/selected-competition @state))}))})

(defmethod mutate 'app/select-class
  [{:keys [state]} _ {:keys [class/id] :as selected-class}]
  {:value  {:keys []}
   :action (fn []
             (let [class
                   (first (filter #(= (:class/id %) id)
                                  (:competition/classes (:app/selected-competition @state))))]
               (swap! state assoc :app/selected-class class)))})

(defmethod mutate 'class/update
  [{:keys [state]} _ {:keys [class/id class/name] :as class-info}]
  {:value  {:keys [:app/selected-class]}
   :action (fn []
             (swap! state (fn [current]
                            (update-in current [:app/selected-class]
                                       (fn [current-selected]
                                         (merge current-selected class-info))))))})