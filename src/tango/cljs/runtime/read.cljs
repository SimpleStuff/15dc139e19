(ns tango.cljs.runtime.read
  (:require
    [om.next :as om]))


(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read

(defmulti read om/dispatch)

(defmethod read :app/selected-competition
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-competition [])
   :query true})

(defmethod read :competition/name
  [{:keys [state]} _ _]
  {:value (:competition/name (get @state :app/selected-competition []))})

(defmethod read :app/status
  [{:keys [state]} _ _]
  {:value (get @state :app/status :undefined)})

(defmethod read :app/selected-page
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-page :home)})

(defmethod read :app/selected-activities
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-activities [])
   :query true})

(defmethod read :app/speaker-activities
  [{:keys [state]} _ _]
  {:value (get @state :app/speaker-activities [])
   :query true})

(defmethod read :app/clients
  [{:keys [state]} _ _]
  {:value (get @state :app/clients [])
   :query true})

(defmethod read :app/selected-class
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-class nil)})

;; TODO - temporary, should be read from the competition
(defmethod read :app/participants
  [{:keys [state]} _ _]
  {:value (get @state :app/participants [])
   :query true})

(defmethod read :app/adjudicator-panels
  [{:keys [state]} _ _]
  {:value (get @state :app/adjudicator-panels [])
   :query true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Dances
(defmethod read :app/dances
  [{:keys [state]} _ _]
  {:value (get @state :app/dances [])
   ;:query true
   })

(defmethod read :app/selected-dance
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-dance [])})