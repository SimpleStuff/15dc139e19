(ns tango.cljs.adjudicator.read
  (:require [om.next :as om]
            [alandipert.storage-atom :as ls]))

(defn log [m]
  (.log js/console m))

(def local-storage (ls/local-storage (atom {}) :local-id))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read

(defmulti read om/dispatch)

(defmethod read :app/selected-activities
  [{:keys [state]} _ _]
  {:value (get @state :app/selected-activities [])
   :query true})

(defmethod read :app/selected-adjudicator
  [{:keys [ast state]} _ _]
  {:value (get @state :app/selected-adjudicator nil)
   :query (do (log (str "AST : " (assoc ast :params {:client/id (:client-id @local-storage)})))
              (assoc ast :params {:client/id (:client-id @local-storage)}))})

(defmethod read :app/results
  [{:keys [state]} _ _]
  {:value (get @state :app/results #{})
   :query true})

(defmethod read :app/heat-page
  [{:keys [state]} _ _]
  {:value (get @state :app/heat-page 1)})

(defmethod read :app/heat-page-size
  [{:keys [state]} _ _]
  {:value (get @state :app/heat-page-size 2)})

(defmethod read :app/status
  [{:keys [state]} _ _]
  {:value (get @state :app/status :loading)})

(defmethod read :app/admin-mode
  [{:keys [state]} _ _]
  {:value (get @state :app/admin-mode false)})

(defmethod read :app/local-id
  [{:keys [state]} _ _]
  {:value (get @state :app/local-id nil)})



