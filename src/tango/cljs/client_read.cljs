(ns tango.cljs.client-read
  (:require [om.next :as om]
            [datascript.core :as d]))

(defn log [m]
  (.log js/console m))

(defmulti read om/dispatch)

(defmethod read :app/competitions
  [{:keys [state query]} _ _]
  {:value (if query
            (d/q '[:find [(pull ?e ?selector) ...]
                   :in $ ?selector
                   :where [?e :competition/name]]
                 (d/db state) query))
   ;:remote true
   })

(defmethod read :app/selected-page
  [{:keys [state]} _ _]
  {:value (d/q '[:find ?page .
                 :where [[:app/id 1] :selected-page ?page]]
               (d/db state))})

(defmethod read :app/selected-competition
  [{:keys [state query]} _ _]
  {:value (if query
            (do
              ;(log "Read Selected Comp")
              (d/q '[:find (pull ?comp ?selector) .
                     :in $ ?selector
                     :where [[:app/id 1] :app/selected-competition ?comp]]
                   (d/db state) query)))
   ;:query true
   })

(defmethod read :app/new-competition
  [{:keys [state query]} _ _]
  {:value (d/q '[:find (pull ?comp ?selector) .
                 :in $ ?selector
                 :where [[:app/id 1] :app/new-competition ?comp]]
               (d/db state) query)})

(defmethod read :app/import-status
  [{:keys [state]} _ _]
  {:value (d/q '[:find ?status .
                 :where [[:app/id 1] :app/import-status ?status]]
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

(defmethod read :app/selected-activity
  [{:keys [state query]} _ _]
  {:value (d/q '[:find [(pull ?a ?selector) ...]
                 :in $ ?selector
                 :where [[:app/id 1] :app/selected-activites ?a]]
               (d/db state) query)})

(defmethod read :app/results
  [{:keys [state query]} _ _]
  (do
    ;(log "Read results ")

    ;; TODO - make sure that results are only for this round
    {:value (do
              (if query
                (let [res
                      (d/q '[:find [(pull ?r selector) ...]
                             :in $ selector
                             :where
                             [[:app/id 1] :app/results ?r]
                             [[:app/id 1] :app/selected-activites ?a]
                             [?a :activity/id ?id]
                             [?r :result/activity ?ra]
                             [?ra :activity/id ?id]
                             ]
                           (d/db state) query)]
                  ;(log query)
                  ;(log "//////////////////////////////////////////")
                  ;(log res)
                  res)))
     :query (let [q (d/q '[:find ?a
                           :where [[:app/id 1] :app/selected-activites ?a]]
                         (d/db state))]
              (if (empty? q)
                false
                true))
     }))

(defmethod read :app/confirmed
  [{:keys [state query]} _ _]
  (do
    ;(log "Read Confirmed")
    {:value (do
              (if query
                (let [res
                      (d/q '[:find [(pull ?c selector) ...]
                             :in $ selector
                             :where
                             [[:app/id 1] :app/confirmed ?c]
                             ;[?c :activity/id ?id]
                             ;[?a :activity/confirmed-by ?adj]
                             ]
                           (d/db state) query)]
                  ;(log query)
                  ;(log "//////////////////////////////////////////")
                  ;(log res)
                  res)))
     :query (let [q (d/q '[:find ?a
                           :where [[:app/id 1] :app/selected-activites ?a]]
                         (d/db state))]
              (if (empty? q)
                false
                true))}))


