(ns tango.cljs.cards
  (:require-macros
    [devcards.core :as dc :refer [defcard deftest]])

  (:require
    [tango.cljs.runtime.core :as rtc]
    [tango.cljs.runtime.mutation :as m]
    [tango.cljs.runtime.read :as r]
    [devcards-om-next.core :refer-macros [defcard-om-next om-next-root]]
    [om.next :as om :refer-macros [defui]]
    [om.dom :as dom]))

; https://anmonteiro.com/2016/02/om-next-meets-devcards-the-full-reloadable-experience/
(enable-console-print!)

(def client-data
  [{:client/id   #uuid "7ba5e71a-46f3-4889-ad0e-f6df7dfd97d0",
    :client/name "Test 0",
    :client/user {}}

   {:client/id   #uuid "9ba5e71a-46f3-4889-ad0e-f6df7dfd97d0",
    :client/name "Test",
    :client/user {:adjudicator/id   #uuid "f6872313-d824-47eb-8d13-af191a8d9651",
                  :adjudicator/name "AA"}}
   {:client/id   #uuid "8ba5e71a-46f3-4889-ad0e-f6df7dfd97d0",
    :client/name "Test 1",
    :client/user {:adjudicator/id   #uuid "f6872313-d824-47eb-8d13-af191a8d9651",
                  :adjudicator/name "BB"}}
   ])

(def panel-date [{:adjudicator-panel/name "3",
                  :adjudicator-panel/id   #uuid "3abb156f-807b-423f-b981-f25aa3b7b094",
                  :adjudicator-panel/adjudicators
                                          [{:adjudicator/id   #uuid "f6872313-d824-47eb-8d13-af191a8d9651",
                                            :adjudicator/name "AA"}
                                           {:adjudicator/id   #uuid "8ec039d9-7cf6-4af0-a3a0-ba633168c06a",
                                            :adjudicator/name "BB"}
                                           {:adjudicator/id   #uuid "0539a91f-d2e1-450f-bfd2-1178787f6adc",
                                            :adjudicator/name "CC"}
                                           {:adjudicator/id   #uuid "4e3cac9d-25e5-4201-9ee3-2a3d77703d5e",
                                            :adjudicator/name "DD"}
                                           {:adjudicator/id   #uuid "19d67188-e0d9-494a-ac18-5eff0dca4bf4",
                                            :adjudicator/name "EE"}
                                           {:adjudicator/id   #uuid "c5bad125-91a8-48b0-8c54-586d536a0a91",
                                            :adjudicator/name "FF"}
                                           {:adjudicator/id   #uuid "91835a32-0073-4ed4-9ec8-371bef8f0e20",
                                            :adjudicator/name "GG"}]}
                 {:adjudicator-panel/name "1",
                  :adjudicator-panel/id #uuid "556b43ad-d8c9-4981-a630-2365f9f5cfec",
                  :adjudicator-panel/adjudicators
                                          [{:adjudicator/id   #uuid "f6872313-d824-47eb-8d13-af191a8d9651",
                                            :adjudicator/name "AA"}
                                           {:adjudicator/id   #uuid "8ec039d9-7cf6-4af0-a3a0-ba633168c06a",
                                            :adjudicator/name "BB"}
                                           {:adjudicator/id   #uuid "0539a91f-d2e1-450f-bfd2-1178787f6adc",
                                            :adjudicator/name "CC"}]}
                 ])
;https://github.com/bhauman/devcards/blob/master/example_src/devdemos/om_next.cljs
;https://github.com/anmonteiro/devcards-om-next/blob/master/src/devcards/devcards_om_next/devcards/core.cljs

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Utils

(defn create-reconciler [opts]
  (om/reconciler
    (merge {:state   {}
            :remotes [:command :query]
            :parser  (om/parser {:read r/read :mutate m/mutate})
            :send    (fn [edn cb] (.log js/console (str "Remote called with " edn)))}
           opts)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Clients cards

(defcard-om-next
  clients-view
  "View of connected clients association.

   An unassigned client (Test 0) will display \"Select\"."
  rtc/ClientsView
  (create-reconciler {:state {:clients            client-data
                              :adjudicator-panels panel-date}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Classes cards
(defcard-om-next
  class-view
  "View of Classes."
  rtc/ClassesView
  (create-reconciler
    {:state   {:classes [{:class/name "A"
                          :class/id #uuid "16872313-d824-47eb-8d13-af191a8d9651"
                          :class/starting [{:participant/number 1
                                            :participant/name "A"}]}
                         {:class/name "B"
                          :class/id #uuid "26872313-d824-47eb-8d13-af191a8d9651"
                          :class/starting [{:participant/number 1
                                            :participant/name "A"}]}]
               :selected {:class/name "A"
                          :class/id #uuid "16872313-d824-47eb-8d13-af191a8d9651"}}}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Schedule View

;; TODO

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; AdjudicatorPanelsView
(defcard-om-next
  adjudicator-panels
  "Display a view of Adjudicator Panels"
  rtc/AdjudicatorPanelsRow
  (create-reconciler {:state {:adjudicator-panel/id   1
                              :adjudicator-panel/name "A"}
                      :parser (om/parser {:read   (fn [{:keys [state]} key _]
                                                    {:value (get @state key)})
                                          :mutate (fn [])})}))

(defcard
  panelsss
  "Test"
  (rtc/AdjudicatorPanelsss [{:adjudicator-panel/id   1
                             :adjudicator-panel/name "A"}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CreateClassView
(defcard-om-next
  create-class-view
  "Create class view"
  rtc/CreateClassView
  (create-reconciler {:state  {:class/name              "New Class"
                               :class/id                1
                               :class/adjudicator-panel {:adjudicator-panel/id   1
                                                         :adjudicator-panel/name "A"}
                               :class/starting          [{:participant/number 1
                                                          :participant/name   "Rolf Turbo"}]}
                      :parser (om/parser {:read (fn [{:keys [state]} key _]
                                                  {:value (get @state key)})
                                          :mutate (fn [])})})
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; TestCard
;(defcard mumbo-jumbo
;         "Mumbo Jumbo"
;         "Mubo")
;
;(deftest test-testing
;         "## Examples"
;         (dc/testing "A test"
;                     (is (= 1 1))))

;;;;;;;;;;;;;;;;;;;;;;;;

;; computed

;;; use ^:once meta in `defui`
;(defui ^:once Counter
;  Object
;  (initLocalState [this]
;    {:val 1})
;  (render [this]
;    (let [{:keys [val]} (om/get-state this)
;          text (:text (om/props this))
;          comp (:comp (om/get-computed this))]
;      (dom/div nil
;        (str "val: " val)
;        (dom/h3 nil text)
;        (dom/h3 nil comp)
;        (dom/button
;          #js {:onClick #(om/update-state! this update :val inc)}
;          "inc!")))))
;

;;; the usual `defcard` calls `om-next-root`
;(defcard om-next-root-example-counter
;         "`om-next-root` takes a component class and (optionally)
;          a map with the state or a reconciler"
;         (om-next-root Counter (om/computed {:text "Test"} {:comp 22})))
;