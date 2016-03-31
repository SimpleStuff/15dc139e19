(ns tango.cljs.adjudicator.read
  (:require [om.next :as om]
            [datascript.core :as d]))

(defmulti read om/dispatch)

(defmethod read :app/selected-page
  [{:keys [state]} _ _]
  {:value (d/q '[:find ?page .
                 :where [[:app/id 1] :selected-page ?page]]
               (d/db state))})

(defmethod read :app/status
  [{:keys [state]} _ _]
  {:value (d/q '[:find ?status .
                 :where [[:app/id 1] :app/status ?status]]
               (d/db state))})

(defmethod read :app/online?
  [{:keys [state]} _ _]
  {:value (d/q '[:find ?online .
                 :where [[:app/id 1] :app/online? ?online]]
               (d/db state))})

(defn log [m]
  (.log js/console m))

(defmethod read :app/selected-activity
  [{:keys [state query ast]} _ _]
  {:value (do
            ;(log "DB ")
            ;(log (d/db state))
            (log "Reee")
            (log query)
            (log ast)
            (if query
              (d/q '[:find (pull ?a selector) .
                     :in $ selector
                     :where [[:app/id 1] :app/selected-activity ?a]]
                   (d/db state) query)))

   :query true})


