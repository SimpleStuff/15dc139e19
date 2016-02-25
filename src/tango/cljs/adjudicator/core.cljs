(ns tango.cljs.adjudicator.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [datascript.core :as d]
            [tango.ui-db :as uidb]
            [tango.domain :as domain]
            [tango.presentation :as presentation]
            [tango.cljs.adjudicator.mutation :as m]
            [tango.cljs.adjudicator.read :as r]))

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB
(defonce conn (d/create-conn uidb/schema))

(defn init-app []
  (d/transact! conn [{:db/id -1 :app/id 1}
                     {:db/id -1 :app/online? false}
                     ;{:db/id -1 :selected-page :competitions}
                     ;{:db/id -1 :app/import-status :none}
                     {:db/id -1 :app/status :running}
                     ;{:db/id -1 :app/selected-competition {}}
                     ]))

(defn app-started? [conn]
  (seq (d/q '[:find ?e
              :where
              [?e :app/id 1]] (d/db conn))))

(defn app-online? [conn]
  (d/q '[:find ?online .
         :where
         [_ :app/online? ?online]] (d/db conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

(defui MainComponent
  static om/IQuery
  (query [_]
    [:app/status])
  Object
  (render
    [this]
    (let [app (om/props this)]
      (log app)
      (dom/div nil
        (dom/h3 nil "Adjudicator UI")
        (dom/span nil
          (dom/label nil "Command Test : ")
          (dom/button #js {:onClick #(log "Command")} "Command"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

;; Init db etc if it has not been done
(when-not (app-started? conn)
  (init-app))

(def reconciler
  (om/reconciler
    {:state   conn
     :remotes [:remote]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    #()}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))