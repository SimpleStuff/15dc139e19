(ns tango.cljs.cards
  (:require-macros
    [devcards.core :as dc :refer [defcard deftest defcard-om-next]])

  (:require
    [tango.cljs.runtime.core :as rt]
    [om.next :as om :refer-macros [defui]]
    [om.dom :as dom]))

; https://anmonteiro.com/2016/02/om-next-meets-devcards-the-full-reloadable-experience/
(enable-console-print!)

(defcard first-card
         "test")

(def client-data
  [{:client/id   #uuid "9ba5e71a-46f3-4889-ad0e-f6df7dfd97d0",
    :client/name "Test",
    :client/user {:adjudicator/id   #uuid "f6872313-d824-47eb-8d13-af191a8d9651",
                  :adjudicator/name "AA"}}])

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
                 {:adjudicator-panel/name "1", :adjudicator-panel/id #uuid "556b43ad-d8c9-4981-a630-2365f9f5cfec",
                  :adjudicator-panel/adjudicators
                                          [{:adjudicator/id   #uuid "f6872313-d824-47eb-8d13-af191a8d9651",
                                            :adjudicator/name "AA"}
                                           {:adjudicator/id   #uuid "8ec039d9-7cf6-4af0-a3a0-ba633168c06a",
                                            :adjudicator/name "BB"}
                                           {:adjudicator/id   #uuid "0539a91f-d2e1-450f-bfd2-1178787f6adc",
                                            :adjudicator/name "CC"}]}
                 ])
;https://github.com/bhauman/devcards/blob/master/example_src/devdemos/om_next.cljs
(defcard-om-next om-component-card
                 "render a om component"
                 rt/ClientsView
                 ;(om/factory rt/ClientsView {:keyfn random-uuid})
                 {:clients            client-data
                  :adjudicator-panels panel-date})

(defcard-om-next classes-card
                 "rendering of classes"
                 rt/ClassesView
                 {:classes [{:class/name "A"
                             :class/starting [{:participant/number 1
                                               :participant/name "A"}]}]})

(defn main []
  )
