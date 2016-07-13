(ns tango.cljs.runtime.core
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
            [tango.cljs.runtime.mutation :as m]
            [tango.cljs.runtime.read :as r]
            [alandipert.storage-atom :as ls])

  (:import [goog.net XhrIo]))

(defn log [m]
  (.log js/console m))

(declare reconciler)
(declare app-state)
(declare update-ch)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente Socket setup
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
                                  {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state)                                    ; Watchable, read-only atom
  )



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import

(defn on-file-read [e file-reader]
  (let [result (.-result file-reader)]
    (log "On file read : send :file/import")
    (chsk-send! [:file/import {:content result}])))

(defn on-click-import-file [e read-cb]
  (log "Import clicked")
  (let [file (.item (.. e -target -files) 0)
        r (js/FileReader.)]
    (set! (.-onload r) #(read-cb % r))
    (.readAsText r file)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ScheduleRow

(defui ScheduleRow
  static om/IQuery
  (query [_]
    [:activity/comment :activity/number :activity/time :activity/name :activity/id :activity/position
     {:activity/source
      [:round/class-id {:round/type ['*]} :round/index {:round/status ['*]}
       {:round/starting [:participant/number :participant/id]}
       :round/number-of-heats :round/number-to-recall
       {:round/dances [:dance/name]}
       {:round/panel [:adjudicator-panel/name
                      :adjudicator-panel/id
                      {:adjudicator-panel/adjudicators
                       [:adjudicator/name
                        :adjudicator/id
                        :adjudicator/number]}]}
       {:class/_rounds
        [{:class/rounds
          [{:round/type ['*]} :round/index {:round/status ['*]}]}]}]}])
  Object
  (render
    [this]
    (let [p (om/props this)
          {:keys [time number name starting round heats recall panel type]}
          (presentation/make-time-schedule-activity-presenter
            p
            (first (:class/_rounds (:activity/source (om/props this)))))
          selected? (:is-selected p)
          completed? (= (:round/status (:activity/source (om/props this))) :status/completed)
          ;speaker-activies #{}                                 ;(:speaker-activites (om/props this))
          ;speaker? (seq (filter #(= (:activity/id (om/props this)) (:activity/id %))
          ;                      speaker-activies))
          speaker? (:is-speaker-selected p)
          ]
      (dom/tr #js {:className (if selected? "success" (if completed? "info" ""))}
        (dom/td nil time)
        (dom/td nil number)
        (dom/td nil name)
        (dom/td nil starting)
        (dom/td nil round)
        (dom/td nil heats)
        (dom/td nil recall)
        (dom/td nil panel)
        (dom/td nil type)
        (when-not (= name "")
          (dom/td nil
                  (dom/div #js {:className "control-group"}
                    (dom/button #js {:className (str "btn" (if selected? " btn-success" " btn-default"))
                                     :onClick   #(when-not (or completed? selected?)
                                                  (do
                                                    (log "Selected")
                                                    (om/transact!
                                                      this
                                                      `[(app/select-activity {:activity/id ~(:activity/id p)})
                                                        :app/selected-activities])))}
                                (dom/span #js {:className "glyphicon glyphicon-play"}))

                    (dom/button #js {:className "btn btn-default"
                                     :onClick #(log "Deselect")}
                                (dom/span #js {:className "glyphicon glyphicon-stop"}))

                    (dom/button #js {:className (str "btn" (if speaker? " btn-success" " btn-default"))
                                     :onClick #(when-not (or completed? speaker?)
                                                (do
                                                  (log "Speaker Select")
                                                  (om/transact!
                                                    this
                                                    `[(app/select-speaker-activity {:activity/id ~(:activity/id p)})
                                                      :app/speaker-activities])))}
                                (dom/span #js {:className "glyphicon glyphicon-volume-up"})))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ScheduleView

(defui ScheduleView
  static om/IQuery
  (query [_]
    (into [] (om/get-query ScheduleRow)))
  Object
  (render
    [this]
    (let [p (om/props this)
          activites (sort-by :activity/position (:competition/activities p))]
      (log "Schedule")
      ;(log "Selected")
      ;(log (:selected-activities p))
      (log "Speaker")
      (log (:speaker-activities p))
      (dom/div nil
        (dom/h2 nil "Time Schedule")
        (dom/table
          #js {:className "table table-hover table-condensed"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "20"} "Time")
              (dom/th #js {:width "20"} "#")
              (dom/th #js {:width "200"} "Dansdisciplin")
              (dom/th #js {:width "20"} "Startande")
              (dom/th #js {:width "20"} "Rond")
              (dom/th #js {:width "20"} "Heats")
              (dom/th #js {:width "20"} "Recall")
              (dom/th #js {:width "20"} "Panel")
              (dom/th #js {:width "20"} "Type")
              (dom/th #js {:width "400"} "")))
          (apply dom/tbody nil (map #((om/factory ScheduleRow)
                                      (merge % {:is-selected
                                                (seq (filter (fn [act]
                                                               (= (:activity/id act) (:activity/id %)))
                                                             (:selected-activities p)))
                                                :is-speaker-selected
                                                (seq (filter (fn [act]
                                                               (= (:activity/id act) (:activity/id %)))
                                                             (:speaker-activities p)))
                                                })) activites))
          ;(apply dom/tbody nil (map #((om/factory ScheduleRow)
          ;                            (merge % {:selected-activity (:selected-activity (om/props this))
          ;                                      :speaker-activites (:speaker-activites (om/props this))}))
          ;                          activites))
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; CreateClassView
(defui CreateClassView
  static om/IQuery
  (query [_]
    [:class/name :class/id {:class/starting [:participant/number]}
     {:class/adjudicator-panel [:adjudicator-panel/id :adjudicator-panel/name]}])
  Object
  (render
    [this]
    (let [;selected-class (:selected-class (om/props this))
          selected-class (om/props this)
          {:keys [panels]} (om/get-computed this)
          ;panels (:adjudicator-panels (om/props this))
          ]
      ;; - Class name
      ;; - Adj Panel
      ;; - Dances
      ;; - Startlist (participants)
      (log "Selected class")
      (log selected-class)
      (dom/div #js {:className "col-sm-12"}
        (dom/div #js {:className "col-sm-offset-3"}
          (dom/h3 {:className " sub-header"} "Create new class"))

        (dom/div #js {:className "container"}
          (dom/div #js {:className "form-horizontal"}

            ;; Class name
            (dom/div #js {:className "form-group"}
              (dom/label #js {:className "col-sm-2 control-label"} "Class name")
              (dom/div #js {:className "col-sm-8"}
                (dom/input #js {:className "form-control"
                                :value     (:class/name selected-class)
                                :id        "clientInputName"
                                :onChange (fn [e]
                                            (om/transact! this `[(class/update
                                                                   {:class/id ~(:class/id selected-class)
                                                                    :class/name ~(.. e -target -value)})]))})))

            ;; Adjudicator Panel
            (dom/div #js {:className "form-group"}
              (dom/label #js {:className "col-sm-2 control-label"} "Adjudicator Panel")
              (dom/div #js {:className "col-sm-8"}
                (dom/div #js {:className "dropdown"}
                  (dom/button #js {:className   "btn btn-default dropdown-toggle"
                                   :data-toggle "dropdown"} ""
                              (dom/span #js {:className "selection"}
                                (if (:class/adjudicator-panel selected-class)
                                  (:adjudicator-panel/name (:class/adjudicator-panel selected-class))
                                  "Select"))
                              (dom/span #js {:className "caret"}))
                  (apply dom/ul #js {:className "dropdown-menu" :role "menu"}
                         (map (fn [panel]
                                (dom/li #js {:role    "presentation"
                                             :onClick #(let [selected-panel
                                                             (first
                                                               (filter
                                                                 (fn [p]
                                                                   (= (:adjudicator-panel/id p)
                                                                      (:adjudicator-panel/id panel)))
                                                                 panels))]
                                                        (log (str "Selected panel " selected-panel))
                                                        (om/transact!
                                                          this
                                                          `[(class/update
                                                              {:class/adjudicator-panel ~selected-panel})
                                                            :app/selected-class]))}
                                  (dom/a #js {:role "menuitem"} (:adjudicator-panel/name panel))))
                              (sort-by :adjudicator-panel/name panels))))))

            ;; Dances
            (dom/div #js {:className "form-group"}
              (dom/label #js {:className "col-sm-2 control-label"} "Dances")
              (dom/div #js {:className "col-sm-8"}
                (dom/div nil
                  (dom/button #js {:className "btn btn-default"
                                   :onClick   (fn [_]
                                                (om/transact!
                                                  reconciler
                                                  `[(app/select-page {:selected-page :dances})
                                                    :app/selected-page]))}
                              (dom/span #js {:className "glyphicon glyphicon-edit"}))
                  (dom/p nil (str "Dances : " (count (:class/dances selected-class)))))

                (map #(dom/button #js {:className "col-sm-6 btn btn-default"}
                                  (:dance/name %))
                     (:class/dances selected-class))))

            ;; Participants
            (dom/div #js {:className "form-group"}
              (dom/label #js {:className "col-sm-2 control-label"} "Participants")
              (dom/div #js {:className "col-sm-8"}
                (dom/div nil
                  (dom/button #js {:className "btn btn-default"
                                   :onClick   (fn [e]
                                                (om/transact!
                                                  reconciler
                                                  `[(app/select-page {:selected-page :edit-class-participants})

                                                    :app/selected-page]))}
                              (dom/span #js {:className "glyphicon glyphicon-edit"}))
                  (dom/p nil (str "Starting : " (count (:class/starting selected-class)))))
                ;(dom/ul nil)
                (map #(dom/button #js {:className "col-sm-6 btn btn-default"}
                                  (str (:participant/number %) " - " (:participant/name %)))
                     (sort-by :participant/number (:class/starting selected-class)))))

            ;; Create
            (dom/div #js {:className "form-group"}
              (dom/div #js {:className "col-sm-offset-2 col-sm-8"}
                (dom/button #js {:className "btn btn-default"
                                 :onClick   #(om/transact! this `[(app/select-page
                                                                    {:selected-page :classes})
                                                                  :app/selected-page])}
                            (dom/span #js {:className "glyphicon glyphicon-arrow-left"})
                            " Undo")
                (dom/button
                  #js {:className "btn btn-primary col-sm-offset-8"
                       :type      "submit"
                       :onClick   (fn [e]
                                    (om/transact!
                                      this
                                      `[(class/save {:class ~selected-class})
                                        (app/select-page {:selected-page :classes})
                                        :app/selected-page]))}
                  (dom/span #js {:className "glyphicon glyphicon-ok"})
                  " Save")))))
        ))))

;(dom/div #js {:className "container"}
;  (dom/h3 nil "Assign this client a name to use it as an Adjudicator device")
;  (dom/div #js {:className "form-horizontal"}
;    (dom/div #js {:className "form-group"}
;      (dom/label #js {:className "col-sm-2 control-label"
;                      :htmlFor       "clientInputName"} "Client name")
;      (dom/div #js {:className "col-sm-8"}
;        (dom/input #js {:className "form-control"
;                        :value name
;                        :id        "clientInputName"
;                        :onChange #(om/transact! this `[(app/set-client-info
;                                                          {:client/name ~(.. % -target -value)})])})))
;    (dom/div #js {:className "form-group"}
;      (dom/div #js {:className "col-sm-offset-2 col-sm-10"}
;        (dom/button
;          #js {:className "btn btn-default"
;               :type      "submit"
;               :onClick   #(do
;                            (let [idt (random-uuid)]
;                              (swap! local-storage assoc :client-id idt)
;                              (om/transact! this
;                                            `[(app/set-client-info {:client/id   ~idt
;                                                                    :client/name ~name})
;                                              (app/status {:status :loading})
;                                              :app/status])))}
;          "Connect")))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ClassRow

(defui ClassRow
  static om/IQuery
  (query [_]
    '[:class/position :class/name :class/remaining :class/id
      ;; TODO - in a normalized view only participant/id should be good
      {:class/starting [:participant/number :participant/name :participant/id]}
      {:class/rounds
       [{:round/status [*]}
       {:round/type [*]}]}
     {:class/adjudicator-panel
      [:adjudicator-panel/name
       :adjudicator-panel/id]}
     {:class/dances [*]}])
  Object
  (render [this]
    ;(log "ClassRow")
    ;(log (:class/dances (om/props this)))
    (let [p (om/props this)
          {:keys [selected?]} (om/get-computed this)
          {:keys [position name panel type starting status]}
          (presentation/make-class-presenter p)]
      (dom/tr #js {:className (when selected? "info")
                   :onClick   #(om/transact! this `[(app/select-class {:class/id ~(:class/id p)})
                                                    :selected?
                                                    :selected
                                                    :app/selected-page])}
        (dom/td nil position)
        (dom/td nil name)
        (dom/td nil panel)
        (dom/td nil type)
        (dom/td nil starting)
        (dom/td nil status)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ClassesView
(defui ClassesView
  ;static om/IQuery
  ;(query [_]
  ;  `[:app/selected-class])
  Object
  (render
    [this]
    (let [classes (sort-by :class/position (:classes (om/props this)))
          ;{:keys [selected]} (om/get-computed this)
          selected (:selected (om/props this))]
      (log "ClassView")
      (log selected)
      (dom/div nil
        (dom/h2 {:className "sub-header"} "Classes")
        (dom/div nil
          ;(dom/button #js {:onClick #(om/transact!
          ;                            reconciler
          ;                            `[(class/create {:class/name "New Class"
          ;                                             :class/id ~(random-uuid)})
          ;                              :app/selected-competition])} "New")
          (dom/button #js {:className "btn btn-default"
                           :onClick   (fn [e]
                                        (let [new-id (random-uuid)]
                                          (om/transact!
                                            reconciler
                                            `[(app/select-page {:selected-page :create-class})
                                              (class/create {:class/name "New Class"
                                                             :class/id   ~new-id})
                                              (app/select-class {:class/id ~new-id})
                                              :app/selected-page])))}
                      (dom/span #js {:className "glyphicon glyphicon-plus"}))

          (dom/button #js {:className "btn btn-default"
                           :onClick #(om/transact!
                                      reconciler
                                      `[(class/delete {:class/id
                                                       ~(:class/id selected)})])}
                      (dom/span #js {:className "glyphicon glyphicon-trash"}))

          (dom/button #js {:className "btn btn-default"
                           :onClick   #(om/transact!
                                        reconciler
                                        `[(app/select-class {:class/id ~(:class/id selected)})
                                          (app/select-page {:selected-page :create-class})])}
                      (dom/span #js {:className "glyphicon glyphicon-edit"})))

        (dom/table
          #js {:className "table table-hover table-condensed"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "20"} "#")
              (dom/th #js {:width "200"} "Dansdisciplin")
              (dom/th #js {:width "20"} "Panel")
              (dom/th #js {:width "20"} "Typ")
              (dom/th #js {:width "20"} "Startande")
              (dom/th #js {:width "20"} "Status")))
          ;(apply dom/tbody nil (map #((om/factory ClassRow)
          ;                            (assoc % :selected? (if (= (:class/id %)
          ;                                                       (:class/id selected))
          ;                                                  true
          ;                                                  false))) classes))
          (apply dom/tbody nil (map #((om/factory ClassRow)
                                      (om/computed % {:selected? (if (= (:class/id %)
                                                                        (:class/id selected))
                                                                   true
                                                                   false)})) classes))
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; SelectClassParticipantsView
(defui SelectClassParticipantsView
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [participants (sort-by :participant/number (:participants (om/props this)))
          selected-class (:selected-class (om/props this))
          starting (:class/starting selected-class)
          selected? (fn [participant]
                      (if (seq (filter
                                 #(= (:participant/id %) (:participant/id participant))
                                 starting))
                        true
                        false))]
      (log "SelectedClassParticipantsView")
      (log selected-class)
      (dom/div #js {:className "col-sm-12"}

        (dom/div #js {:className "col-sm-12"}
          (dom/div #js {:className "page-header"}
            (dom/div #js {:className "btn-toolbar pull-right"}
              (dom/div #js {:className "btn-group"}
                (dom/button #js {:type      "button"
                                 :className "btn btn-primary"
                                 :onClick   #(om/transact!
                                              this `[(app/select-page {:selected-page :create-class})
                                                     :app/selected-page])}
                            (dom/span #js {:className "glyphicon glyphicon-ok"})
                            " Done")))
            (dom/h2 nil (str "Select Participants for Class " (:class/name selected-class)))))

        (dom/div #js {:className "col-sm-12"}

          (map #(dom/button #js {:className (str "col-sm-3 btn" (if (selected? %)
                                                                  " btn-primary"
                                                                  " btn-default"))
                                 :onClick   (fn [e]
                                              (let [updated-starting
                                                    (if (selected? %)
                                                      (filter (fn [p]
                                                                (not= (:participant/id p) (:participant/id %)))
                                                              starting)
                                                      (conj starting %))]
                                                (om/transact!
                                                  reconciler
                                                  `[(class/update
                                                      {:class/starting ~updated-starting})
                                                    :app/selected-class])))}
                            (str (:participant/number %) " - " (:participant/name %)))
               participants))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Import/Export Component
(defui ImportExportComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [import-status (:import-status (om/props this))
          read-cb on-file-read]
      (dom/div nil
        (dom/span #js {:className (str "btn btn-default btn-file"
                                       (when (= import-status :importing) " disabled"))}
          "Importera.."
          (dom/input #js {:type     "file"
                          :onChange #(do
                                      (om/transact! this `[(app/set-status {:status :importing})])
                                      (on-click-import-file % read-cb))}))

        (dom/button #js {:className "btn btn-default"
                         :onClick   #(om/transact!
                                      this
                                      `[(app/set-status {:status :requested})
                                        :app/selected-competition])}
                    "Exportera")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Admin view
(defui AdminViewComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [p (om/props this)
          status (:status p)]
      (dom/div nil
        (dom/h1 nil "Admin")
        ((om/factory ImportExportComponent) {:import-status status})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MenuComponent
(defui MenuComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [make-btn-fn
          (fn [page-name page-key]
            (dom/button #js {:onClick
                             #(om/transact! this
                                            `[(app/select-page {:selected-page ~page-key})
                                              :app/selected-page])}
                        page-name))]
      (dom/div nil
        (apply dom/div #js {:className "nav"}
               (map (fn [[name key]] (make-btn-fn name key))
                    [["Home" :home]
                     ["Classes" :classes]
                     ["Time Schedule" :time-schedule]
                     ["Clients" :clients]
                     ["Participants" :participants]
                     ["Dances" :dances]]))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Client Row
(defui ClientRow
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
                                                 reconciler
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
(defui ClientsView
  ;static om/IQuery
  ;(query [_]
  ;  (into [] (om/get-query ClientRow)))
  Object
  (render
    [this]
    (let [clients (sort-by (juxt :client/name :client/id) (:clients (om/props this)))
          panels (:adjudicator-panels (om/props this))]
      (log "clients")
      (log clients)
      (dom/div nil
        (dom/h2 nil "Clients")
        (dom/table
          #js {:className "table table-hover table-condensed"}
          (dom/thead nil
            (dom/tr nil
              (dom/th #js {:width "50"} "Id")
              (dom/th #js {:width "50"} "Name")
              (dom/th #js {:width "50"} "Assigned to Adjudicator")))
          (apply dom/tbody nil (map #((om/factory ClientRow {:keyfn :client/id}) {:client       %
                                                              :adjudicator-panels panels}) clients)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; DancesView
;<div class='page-header'>
;<div class='btn-toolbar pull-right'>
;<div class='btn-group'>
;<button type='button' class='btn btn-primary'>Button Text</button>
;</div>
;</div>
;<h2>Header Text</h2>
;</div>

(defui DancesView
  static om/IQuery
  (query [_]
    [:dance/name :dance/id])
  Object
  (render
    [this]
    (let [dances (:dances (om/props this))
          ;{:keys [selected-class]} (om/get-computed this)
          selected-class (:selected-class (om/props this))
          selected-dance (:selected-dance (om/props this))]
      (log "DancesView")
      (log dances)
      (log selected-dance)
      (dom/div #js {:className "col-sm-12"}

        (dom/div #js {:className "col-sm-12"}
          (dom/div #js {:className "page-header"}
            (dom/div #js {:className "btn-toolbar pull-right"}
              (dom/div #js {:className "btn-group"}
                (dom/button #js {:type      "button"
                                 :className "btn btn-primary"
                                 :onClick   #(om/transact!
                                              this `[(app/select-page {:selected-page :create-class})
                                                     :app/selected-page])}
                            (dom/span #js {:className "glyphicon glyphicon-ok"})
                            " Done")))
            (dom/h1 nil "Dances")))

        (dom/div #js {:className "col-sm-6"}
          (dom/div #js {:className "col-sm-12"}
            (dom/h2 {:className "sub-header col-sm-6"} "Selected")
            (dom/button #js {:className "btn btn-default"
                             :onClick   (fn [_]
                                          (let [updated-dances
                                                (filter #(not= (:dance/id %)
                                                               (:dance/id selected-dance))
                                                        (:class/dances selected-class))]
                                            (om/transact!
                                              reconciler
                                              `[(class/update {:class/dances ~updated-dances})])))}
                        (dom/span #js {:className "glyphicon glyphicon-trash"})))

          (dom/div #js {:className "col-sm-12"}
            (map (fn [dance]
                   (dom/button #js {:className (str "btn"
                                                    (if (seq
                                                          (filter
                                                            #(= (:dance/id dance) (:dance/id selected-dance))
                                                            (:class/dances selected-class)))
                                                      " btn-primary"
                                                      " btn-default"))
                                    :onClick   #(om/transact!
                                                 reconciler
                                                 `[(app/select-dance
                                                     {:dance/id ~(:dance/id dance)})
                                                   :app/selected-dance])}
                               (:dance/name dance))) (:class/dances selected-class))))

        (dom/div #js {:className "col-sm-6"}
          (dom/h2 {:className "sub-header"} "Available")
          (map (fn [dance]
                 (dom/button #js {:className "btn btn-default"
                                  :onClick   #(om/transact!
                                               reconciler
                                               `[(class/update
                                                   {:class/dances ~(conj (:class/dances selected-class)
                                                                         {:dance/name (:dance/name dance)
                                                                          :dance/id   (random-uuid)})}
                                                   :app/selected-class)])}
                             (:dance/name dance))) dances)))
      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Participant Row
(defui ParticipantRow
  static om/IQuery
  (query [_]
    [:participant/id :participant/number :participant/name])
  Object
  (render
    [this]
    (let [participant (om/props this)
          name (:participant/name participant)
          number (:participant/number participant)]
      ;(log "Participant Row")
      ;(log participant)
      (dom/tr nil
        ;(dom/td nil (str client-id))
        (dom/td nil number)
        (dom/td nil name)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; ParticipantsView
(defui ParticipantsView
  static om/IQuery
  (query [_]
    (into [] (om/get-query ParticipantRow)))
  Object
  (render
    [this]
    (let [participants (om/props this)]
      (log "participants")
      (log participants)
      (dom/div nil
        (dom/h2 nil "Participants")
        (dom/table
          #js {:className "table table-hover"}
          (dom/thead nil
            (dom/tr nil
              ;(dom/th #js {:width "50"} "Id")
              (dom/th #js {:width "20"} "Number")
              (dom/th #js {:width "50"} "Name")))
          (apply dom/tbody nil (map #((om/factory ParticipantRow {:key-fn :participant/id}) %)
                                    participants)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MainComponent

(def class-view (om/factory ClassesView))

;; https://awkay.github.io/om-tutorial/
(defui MainComponent
  static om/IQuery
  (query [_]
    `[{:app/selected-competition
       [:competition/name :competition/location :competition/id
        {:competition/classes ~(om/get-query ClassRow)}
        {:competition/panels [:adjudicator-panel/name
                              :adjudicator-panel/id
                              {:adjudicator-panel/adjudicators
                               [:adjudicator/id
                                :adjudicator/name]}]}
        {:competition/participants ~(om/get-query ParticipantsView)}
        {:competition/activities ~(om/get-query ScheduleView)}]}

     ;; TODO - participants should come from the competition
      {:app/participants ~(om/get-query ParticipantsView)}

      {:app/dances ~(om/get-query DancesView)}
      {:app/selected-dance ~(om/get-query DancesView)}

      :app/status
      :app/selected-page

      {:app/selected-class ~(om/get-query CreateClassView)}
      ;{:app/selected-class ~(om/get-query ClassRow)}

     {:app/selected-activities [:activity/id] }
     {:app/speaker-activities [:activity/id] }
     {:app/clients ~(om/get-query ClientRow)}
     ])
  Object
  (render
    [this]
    (let [p (om/props this)
          selected-competition (:app/selected-competition p)
          status (:app/status p)
          selected-page (:app/selected-page p)
          participants (:app/participants p)
          panels (:competition/panels selected-competition)
          selected-class (:app/selected-class p)]
      (log "Main Selected :")
      (log selected-class)
      (dom/div nil
        ((om/factory MenuComponent))
        (condp = selected-page
          :home (dom/div nil
                  (dom/h1 nil (str "Runtime of " (:competition/name selected-competition)))
                  (dom/h4 nil (str "Competition Id : " (:competition/id selected-competition)))
                  ((om/factory AdminViewComponent) {:status status}))

          :classes (class-view
                     ;(om/computed (:competition/classes selected-competition)
                     ;                       {:selected selected-class})
                     {:classes (:competition/classes selected-competition)
                      :selected selected-class}
                     )

          :create-class ((om/factory CreateClassView)
                          ;                selected-class
                          (om/computed selected-class {:panels panels}))

          :edit-class-participants ((om/factory SelectClassParticipantsView)
                                     {:participants participants
                                      :selected-class (:app/selected-class p)})

          :dances ((om/factory DancesView)
                    {:dances (:app/dances p)
                     :selected-class (:app/selected-class p)
                     :selected-dance (:app/selected-dance p)}
                    ;(om/computed (:app/dances p) {:selected-class selected-class})
                    )

          :time-schedule ((om/factory ScheduleView) {:competition/activities
                                                     (:competition/activities selected-competition)
                                                     :selected-activities
                                                     (:app/selected-activities p)
                                                     :speaker-activities
                                                     (:app/speaker-activities p)})
          :clients ((om/factory ClientsView) {:clients      (:app/clients p)
                                              :adjudicator-panels (:competition/panels selected-competition)})

          :participants ((om/factory ParticipantsView) participants))
        ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote com
(defn transit-post [url edn cb]
  (log edn)
  (.send XhrIo url
         #()                                                ;log
         ;(this-as this
         ;  (log (t/read (t/reader :json)
         ;               (.getResponseText this)))
         ;  (cb (t/read (t/reader :json) (.getResponseText this))))

         "POST" (t/write (t/writer :json) edn)
         #js {"Content-Type" "application/transit+json"}))

(defn remote-send []
  (fn [edn cb]
    ;; TODO - seems that om will put togheter both command and query when needed
    ;;  so it should be possible to put them togheter on serverside
    (cond
      (:command edn)
      ;(log "a")
      (do
        (log (:command edn))
        (transit-post "/commands" edn cb))
      (:query edn)
      (go
        ;(log "Query")
        ;(log edn)
        (let [remote-query (:query edn)
              ;remote-query (if (map? (first (:query edn)))
              ;               (:query edn)
              ;               ;; TODO - why do we not get a good query
              ;               (conj [] (first (om/get-query MainComponent))))
              response (async/<! (http/get "/query" {:query-params
                                                     {:query (pr-str remote-query)}}))
              edn-response (second (cljs.reader/read-string (:body response)))]

          ;(log remote-query)
          ;(log edn-response)
          ;; TODO - why is the response a vec?
          ;(cb (cljs.reader/read-string (:body response)))
          (cb edn-response)
          )))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente message handling

; Dispatch on event-id
(defmulti event-msg-handler :id)

;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:keys [id ?data event] :as ev-msg}]
  (event-msg-handler {:id    (first ev-msg)
                      :?data (second ev-msg)}))

(defmethod event-msg-handler :default
  [ev-msg]
  (log (str "Unhandled socket event: " ev-msg)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (do
    (log (str "Channel socket state change: " ?data))
    (when (:first-open? ?data)
      (log "Channel socket successfully established!"))))

;; TODO - Cleaning when respons type can be separated
(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [[topic payload] ?data]
    (log (str "Socket Event from server: " topic))
    (when (= topic :event-manager/transaction-result)
      (do (log "Time to re-query")
          (om/transact! reconciler
                        `[(app/set-status {:status :requested})
                          ;; TODO - consolidate with main query
                          {:app/selected-competition
                           ~(into [:competition/name :competition/location
                                  {:competition/panels [:adjudicator-panel/name
                                                        {:adjudicator-panel/adjudicators
                                                         [:adjudicator/id
                                                          :adjudicator/name]}]}]
                                 (concat (om/get-query ScheduleView)))}])))
    (when (= topic :tx/accepted)
      (log (str "Socket Event from server: " topic))
      ;(log (str "Socket Payload: " payload))
      (cond
        (= payload 'app/set-speaker-activity)
        (do
          ;(log "select")
          (om/transact! reconciler `[:app/speaker-activites]))
        (= payload 'app/set-client-info)
        (do
          (log (str "Client info changed " payload))
          (om/transact! reconciler `[{:app/clients ~(om/get-query ClientsView)}]))))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Socket Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(defonce app-state (atom {:app/selected-competition nil
                          :app/status :loaded
                          :app/selected-page :home
                          :app/selected-activities #{}
                          :app/speaker-activities #{}
                          :app/selected-class nil
                          :app/dances [{:dance/name "Samba"
                                        :dance/id #uuid "d1edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}
                                       {:dance/name "Mango"
                                        :dance/id #uuid "d2edcf5d-1a8b-423e-9d6b-5cda00ff1b6e"}]}))

(def reconciler
  (om/reconciler
    {:state   app-state
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (remote-send)}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))
