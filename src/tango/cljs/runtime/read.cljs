(ns tango.cljs.runtime.read
  (:require
    [om.next :as om]))

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

