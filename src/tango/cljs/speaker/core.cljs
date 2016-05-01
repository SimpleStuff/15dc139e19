(ns tango.cljs.speaker.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])

  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]

            [cognitect.transit :as t]

            [cljs.core.async :as async :refer (<! >! put! chan timeout)]
            [cljs-http.client :as http]

            [taoensso.sente :as sente :refer (cb-success?)]
            [datascript.core :as d]
            [tango.ui-db :as uidb]
            [tango.domain :as domain]
            [tango.presentation :as presentation]
            [tango.cljs.speaker.mutation :as m]
            [tango.cljs.speaker.read :as r]
            [alandipert.storage-atom :as ls])

  (:import [goog.net XhrIo]))

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MainComponent

;; Activity name       Round 1
;; type? (medium)

;; Heat 1: 1880
;; Heat 2: 1888

;; Total 31
;; Recall 18
;; Judges A, B, C      Event 3A

(defui MainComponent
  static om/IQuery
  (query [_])
  Object
  (render
    [this]
    (dom/div nil "SPEAKe")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(defonce app-state (atom {}))

(def reconciler
  (om/reconciler
    {:state   app-state
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     ;:send    (transit-post "/commands")
     }
    ))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))