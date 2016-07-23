(ns devcards.utils
  (:require [om.next :as om]
            [tango.cljs.runtime.read :as r]
            [tango.cljs.runtime.mutation :as m]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

(defn create-reconciler [opts]
  (om/reconciler
    (merge {:state   {}
            :remotes [:command :query]
            :parser  (om/parser {:read r/read :mutate m/mutate})
            :send    (fn [edn cb] (.log js/console (str "Remote called with " edn)))}
           opts)))
