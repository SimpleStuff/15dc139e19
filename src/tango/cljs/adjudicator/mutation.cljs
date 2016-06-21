(ns tango.cljs.adjudicator.mutation
  (:require
    [om.next :as om]
    [alandipert.storage-atom :as ls]))


(defn log [m]
  (.log js/console m))

(def local-storage (ls/local-storage (atom {}) :local-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/select-adjudicator
  [{:keys [state]} _ {:keys [adjudicator]}]
  {:value  {:keys [:app/selected-adjudicator]}
   :action (fn []
             (do
               (log "Pre select")
               (swap! state assoc :app/selected-adjudicator adjudicator)
               (log "Post select")))})

(defmethod mutate 'app/heat-page
  [{:keys [state]} _ {:keys [page]}]
  {:value  {:keys [:app/heat-page]}
   :action (fn []
             (swap! state assoc :app/heat-page page))})

(defmethod mutate 'participant/set-result
  [{:keys [state]} _ {:keys [id] :as result}]
  {:value  {:keys [:app/results]}
   :action (fn []
             ;; TODO - normalize with Om instead
             (swap! state (fn [current]
                            (let [clean-result (filter #(not= (:result/id %) (:result/id result))
                                                       (:app/results current))]
                              (update-in current [:app/results] #(conj clean-result result)))))

             ;(log (:app/results @state))
             )
   :command true})

(defmethod mutate 'app/confirm-marks
  [{:keys [state]} _ {:keys [results]}]
  {:value   {:keys []}
   :action  (fn [] (swap! state assoc :app/status :confirming))
   :command true})

(defmethod mutate 'app/status
  [{:keys [state]} _ {:keys [status]}]
  {:value  {:keys [:app/status]}
   :action (fn []
             (swap! state assoc :app/status status)
             (log (:app/status @state)))})

(defmethod mutate 'app/set-admin-mode
  [{:keys [state]} _ {:keys [in-admin]}]
  {:value  {:keys [:app/admin-mode]}
   :action (fn [] (swap! state assoc :app/admin-mode in-admin))})

;(defmethod mutate 'app/set-local-id
;  [{:keys [state]} _ {:keys [id]}]
;  {:value {:keys [:app/local-id]}
;   :action (fn []
;             (swap! state assoc :app/local-id id)
;             (swap! local-storage assoc :client-id id))})

(defmethod mutate 'app/set-client-info
  [{:keys [state]} _ {:keys [client/name client/id] :as client}]
  {:value   {:keys [:app/client]}
   :action  (fn []
              ;(swap! state (fn [current]
              ;               (merge current {:app/local-id    id
              ;                               :app/client-name name})))
              (swap! state assoc :app/client client)
              (log (str "client changed " (:app/client @state)))
              ;(swap! local-storage assoc :client-id id)
              ;(log (str "Local changed : " (:client-id @local-storage)))
              )
   ;:command true
   })
