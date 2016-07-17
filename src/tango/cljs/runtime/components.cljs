(ns tango.cljs.runtime.components
  (:require [om.dom :as dom]
            [om.next :as om :refer-macros [defui]]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client Row
(defui ^:once ClientRow
  static om/IQuery
  (query [_]
    [:client/id :client/name {:client/user [:adjudicator/id :adjudicator/name]}])
  Object
  (render
    [this]
    (let [client (:client (om/props this))
          panels (sort-by :adjudicator-panel/name (:adjudicator-panels (om/props this)))
          client-id (:client/id client)
          client-name (:client/name client)
          adjudicator-id (:adjudicator/id (:client/user client))
          adjudicator-name (:adjudicator/name (:client/user client))]
      (dom/tr nil
        (dom/td nil (str client-id))
        (dom/td nil client-name)
        (dom/td nil
          (dom/div #js {:className "dropdown"}
            (dom/button #js {:className   "btn btn-default dropdown-toggle"
                             :data-toggle "dropdown"} ""
                        (dom/span #js {:className "selection"}
                          (if adjudicator-name adjudicator-name "Select"))
                        (dom/span #js {:className "caret"}))
            ;(dom/ul #js {:className "dropdown-menu"
            ;             :role "menu"}
            ;  (dom/li #js {:className "dropdown-header"} (str "Panel - "
            ;                                                  (:adjudicator-panel/name (first panels))))
            ;  (dom/li #js {:role "presentation"}
            ;    (dom/a #js {:role "menuitem"} "AA"))
            ;  (dom/li #js {:role "presentation"}
            ;    (dom/a #js {:role "menuitem"} "BB")))

            (apply dom/ul #js {:className "dropdown-menu" :role "menu"}
                   (map (fn [panel]
                          [(dom/li #js {:className "dropdown-header"}
                             (str "Panel - " (:adjudicator-panel/name panel)))
                           (map (fn [adjudicator]
                                  (dom/li
                                    #js {:role "presentation"
                                         :onClick     ;#(log (str "Change ajd to " (:adjudicator/name adjudicator)))
                                               #(om/transact!
                                                 this
                                                 `[(app/set-client-info
                                                     {:client/id   ~client-id
                                                      :client/name ~client-name
                                                      :client/user
                                                                   {:adjudicator/id   ~(:adjudicator/id adjudicator)
                                                                    :adjudicator/name ~(:adjudicator/name adjudicator)}})
                                                   :app/clients])
                                         }
                                    (dom/a #js {:role "menuitem"} (:adjudicator/name adjudicator))))
                                (:adjudicator-panel/adjudicators panel))])
                        panels))
            ))
        ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Clients View
(defui ^:once ClientsView
       ;static om/IQuery
       ;(query [_]
       ;  (into [] (om/get-query ClientRow)))
       Object
       (render
         [this]
         (let [clients (sort-by (juxt :client/name :client/id) (:clients (om/props this)))
               panels (:adjudicator-panels (om/props this))]
           (dom/div nil
                    (dom/h2 nil "Clients")
                    (dom/table
                      #js {:className "table table-hover table-condensed"}
                      (dom/thead nil
                                 (dom/tr nil
                                         (dom/th #js {:width "50"} "Id")
                                         (dom/th #js {:width "50"} "Name")
                                         (dom/th #js {:width "50"} "Assigned to Adjudicator")))
                      (apply dom/tbody nil (map #((om/factory ClientRow {:keyfn (fn [_] (:client/id %))})
                                                  {:client       %
                                                   :adjudicator-panels panels}) clients)))))))

