 (ns devcards.adjudicators
   (:require-macros
     [devcards.core :as dc :refer [defcard deftest]])

   (:require
     [tango.cljs.runtime.core :as rtc]
     [tango.cljs.runtime.mutation :as m]
     [tango.cljs.runtime.read :as r]
     [devcards.utils :as u]
     [devcards-om-next.core :refer-macros [defcard-om-next om-next-root]]
     [om.next :as om]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjudicatorPanelsView
(defcard-om-next
  adjudicator-panels
  "Display a Adjudicator Panel Row. Makes sure that the components query is correct."
  rtc/AdjudicatorPanelsRow
  (u/create-reconciler {:state {:adjudicator-panel/id   1
                              :adjudicator-panel/name "A"
                              :adjudicator-panel/adjudicators [{:adjudicator/name "Rolf"
                                                                :adjudicator/number 1
                                                                :adjudicator/id 1}]}
                      :parser (om/parser {:read   (fn [{:keys [state]} key _]
                                                    {:value (get @state key)})
                                          :mutate (fn [])})}))

(defcard
  adjudicator-panels
  "Display all panels."
  (rtc/AdjudicatorPanels
    [{:adjudicator-panel/id   1
      :adjudicator-panel/name "A"
      :adjudicator-panel/adjudicators
                              [{:adjudicator/name "Rolf Med Långtnamn" :adjudicator/number 1  :adjudicator/id 1}
                               {:adjudicator/name "Flor" :adjudicator/number 2 :adjudicator/id 2}
                               {:adjudicator/name "Olfr" :adjudicator/number 3 :adjudicator/id 3}
                               {:adjudicator/name "Lofr" :adjudicator/number 4 :adjudicator/id 4}]}
     {:adjudicator-panel/id   2
      :adjudicator-panel/name "B"
      :adjudicator-panel/adjudicators
                              [{:adjudicator/name "Rolf" :adjudicator/number 1 :adjudicator/id 1}
                               {:adjudicator/name "Flor" :adjudicator/number 2 :adjudicator/id 2}
                               {:adjudicator/name "Lofr" :adjudicator/number 4 :adjudicator/id 3}]}]))

(defcard-om-next
  create-panel
  "Create and edit panels"
  rtc/CreatePanelView
  (u/create-reconciler
    {:state {:adjudicator-panel/id   1
             :adjudicator-panel/name "New Panel"
             :adjudicator-panel/adjudicators
                                     [{:adjudicator/name "Rolf Med Långtnamn" :adjudicator/number 1 :adjudicator/id 1}
                                      {:adjudicator/name "Flor" :adjudicator/number 2 :adjudicator/id 2}
                                      {:adjudicator/name "Olfr" :adjudicator/number 3 :adjudicator/id 3}
                                      {:adjudicator/name "Lofr" :adjudicator/number 4 :adjudicator/id 4}]}
     :parser (om/parser {:read   (fn [{:keys [state]} key _]
                                   {:value (get @state key)})
                         :mutate (fn [])})}))

(defcard-om-next
  select-panel-adjudicators
  "Selecting panel adjudicators"
  rtc/SelectPanelAdjudicatorsView)

(defcard
  adjudicators
  "View and create Adjudicators"
  (fn [data-atom owner] (rtc/adjudicators-view (:adjudicators @data-atom) (:selected @data-atom)
                                               (fn [_ selected]
                                                 (swap! data-atom merge {:selected selected}))))
  (atom {:adjudicators [{:adjudicator/name "Rolf Med Långtnamn" :adjudicator/number 1 :adjudicator/id 1}
                        {:adjudicator/name "Flor" :adjudicator/number 2 :adjudicator/id 2}
                        {:adjudicator/name "Olfr" :adjudicator/number 3 :adjudicator/id 3}
                        {:adjudicator/name "Lofr" :adjudicator/number 4 :adjudicator/id 4}]
         :selected     {:adjudicator/name "Flor" :adjudicator/number 2 :adjudicator/id 2}}))