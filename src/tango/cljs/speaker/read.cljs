(ns tango.cljs.speaker.read
  (:require
    [om.next :as om]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read

(defmulti read om/dispatch)

(defmethod read :app/speaker-activites
  [{:keys [state]} _ _]
  {:value (get @state :app/speaker-activites [])
   :query true})

(defmethod read :app/filter
  [{:keys [state]} _ _]
  {:value (get @state :app/filter [])})
