(ns tango.cljs.speaker.read
  (:require
    [om.next :as om]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read

(defmulti read om/dispatch)

;(defmethod read :app/selected-page
;  [{:keys [state]} _ _]
;  {:value (d/q '[:find ?page .
;                 :where [[:app/id 1] :selected-page ?page]]
;               (d/db state))})