(ns tango.cljs.adjudicator.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]

            [cognitect.transit :as t]

            [cljs.core.async :as async :refer (<! >! put! chan timeout)]
            [cljs-http.client :as http]

            [taoensso.sente :as sente :refer (cb-success?)]

            [tango.domain :as domain]
            [tango.cljs.adjudicator.mutation :as m]
            [tango.cljs.adjudicator.read :as r]
            [alandipert.storage-atom :as ls]
            [cljs.tools.reader.edn :as edn])

  (:import [goog.net XhrIo]))

(defn log [m]
  (.log js/console m))

(defn log-command [m level]
  (do
    (.send XhrIo "/commands"
           #()                              ;log  ;; TODO - Should do something with the response..
           "POST" (t/write (t/writer :json) {:command `[(app/log {:message ~m :level ~level})]})
           #js {"Content-Type" "application/transit+json"})
    (.log js/console m)))


(defn log-trace [m]
  (log-command m :trace))

(defn log-info [m]
  (log-command m :info))

(defn log-debug [m]
  (log-command m :debug))

(declare reconciler)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente Socket setup
;(log-trace "Begin Sente Socket Setup")

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
                                  {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state)                                    ; Watchable, read-only atom
  )

;(log-trace "End Sente Socket Setup")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Local storage
;(log-trace "Begin Local Storage")

(def local-storage (ls/local-storage (atom {}) :local-id))

(log (str "Client id : " @local-storage))

;(log (str "Local Adjudicator of Client : " (:name @local-id) " - "
;          (:adjudicator/id (:adjudicator @local-id))))

;(log-trace "End Local Storage")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
;(log-trace "Begin Components")

(defui AdjudicatorSelection
  static om/IQuery
  (query [_]
    [:adjudicator-panel/name
     :adjudicator-panel/id
     {:adjudicator-panel/adjudicators [:adjudicator/name
                                       :adjudicator/id]}])
  Object
  (render [this]
    (let [client (om/props this)]
      (log "AdjudicatorSelection")
      (dom/div nil
        (dom/h3 nil
          (str "Waiting for Administrator to assign an adjudicator to this client ("
               (:client/name client)
               ")"))))))

(defui HeatRowComponent
  static om/IQuery
  (query [_]
    [:participant/id :participant/number
     :app/results])
  Object
  (render [this]
    (let [result (:result (om/props this))
          point (if (:result/point result) (:result/point result) 0)
          mark-x (if (:result/mark-x result) (:result/mark-x result) false)]
      (dom/div #js {:className "row"}
        (dom/div #js {:className "col-xs-2"}
          (dom/h3 #js {:className "control-label"} (str (:participant/number (om/props this)))))

        ;; TODO - change on + and - should also send a result
        ;(dom/div #js {:className "form-group "})
        (dom/label #js {:className "col-xs-3"}
          (dom/div #js {:className ""}
            (dom/div
              #js {:className (if (and (not (:allow-marks? (om/props this)))
                                       (not mark-x))
                                "mark-disabled"
                                "mark")
                   :disabled  true}
              (dom/h1 #js {:className "mark-text"
                           } (if (:result/mark-x (:result (om/props this)))
                               "X"
                               ""))))

          (dom/input #js {:type     "checkbox"
                          :checked  (:result/mark-x (:result (om/props this)))

                          :disabled (and (not (:allow-marks? (om/props this)))
                                         (not mark-x))
                          :onChange #(let [mark-value (if (or (:allow-marks? (om/props this))
                                                              (and (not (:allow-marks? (om/props this)))
                                                                   mark-x))
                                                        (.. % -target -checked)
                                                        mark-x)]
                                      (om/transact!
                                        reconciler
                                        `[(participant/set-result
                                            {:result/id          ~(if (:result/id (:result (om/props this)))
                                                                    (:result/id (:result (om/props this)))
                                                                    (random-uuid))
                                             :result/mark-x      ~mark-value
                                             :result/point       ~point
                                             :result/participant {:participant/id ~(:participant/id (om/props this))}
                                             :result/activity    {:activity/id ~(:activity/id (om/props this))}
                                             :result/adjudicator {:adjudicator/id ~(:adjudicator/id (om/props this))}})
                                          :app/results]))})
          )

        (let [set-result-fn (fn [transform-fn]
                              (om/transact!
                                reconciler
                                `[(participant/set-result
                                    {:result/id          ~(if (:result/id (:result (om/props this)))
                                                            (:result/id (:result (om/props this)))
                                                            (random-uuid))
                                     :result/mark-x      ~mark-x
                                     :result/point       ~(transform-fn point)
                                     :result/participant {:participant/id ~(:participant/id (om/props this))}
                                     :result/activity    {:activity/id ~(:activity/id (om/props this))}
                                     :result/adjudicator {:adjudicator/id ~(:adjudicator/id (om/props this))}})
                                  :app/results]))]
          (dom/div #js {:className "col-xs-7"}
            (dom/button #js {:type      "button"
                             :className "btn btn-default btn-lg col-xs-4"
                             :onClick   #(set-result-fn inc)} "+")

            (dom/button #js {:type      "button"
                             :className "btn btn-default btn-lg col-xs-4"
                             :onClick   #(set-result-fn dec)} "-")

            (when (not= 0 point)
              (dom/h3 #js {:className "col-xs-2"} (str point)))))))))

(defui HeatComponent
  static om/IQuery
  (query [_]
    [{:round/heats [:heat/number
                    {:heat/participants (om/get-query HeatRowComponent)}]}])
  Object
  (render [this]
    (let [heat (:heat (om/props this))
          participants (:participants (om/props this))
          adjudicator-id (:adjudicator/id (om/props this))
          activity-id (:activity/id (om/props this))
          results (:results (om/props this))
          find-result (fn [participant]
                        (first (filter (fn [res] (= (:participant/id participant)
                                                    (:participant/id (:result/participant res))))
                                       results)))]
      (log "Render HeatComponent")
      ;(log "Result")
      ;(log (:results (om/props this)))
      ;(log "Participants")
      ;(log (:participants (om/props this)))
      (dom/div #js {:className "col-xs-6"}
        (dom/h2 #js {:className "text-center"} "Heat " (str (+ 1 heat)))
        (map #((om/factory HeatRowComponent)
               (merge % {:adjudicator/id adjudicator-id
                         :activity/id    activity-id
                         :result         (find-result %)
                         :allow-marks? (:allow-marks? (om/props this))}))
             participants)
        ))))

(defui HeatsComponent
  static om/IQuery
  (query [_]
    [:participant/id :participant/number])
  Object
  (render [this]
    (let [participants (:participants (om/props this))
          heats (:heats (om/props this))
          heat-parts (domain/create-distribution
                       (sort-by :participant/number participants)
                       heats)
          page-size (:heat-page-size (om/props this))
          current-page (:heat-page (om/props this))
          page-start (* page-size current-page)
          page-end (+ page-start page-size)]
      (log "Render HeatsComponent")
      (dom/div nil
        (dom/div #js {:className "col-xs-12"}
          (subvec
            (vec (map-indexed (fn [idx parts] ((om/factory HeatComponent {:keyfn (fn [_] idx)})
                                                {:heat           idx
                                                 :participants   parts
                                                 :adjudicator/id (:adjudicator/id (om/props this))
                                                 :activity/id    (:activity/id (om/props this))
                                                 :results        (:results (om/props this))
                                                 :allow-marks?   (:allow-marks? (om/props this))}))
                              heat-parts))
            page-start (min page-end (int heats))))))))

(defui HeatsControll
  static om/IQuery
  (query [_])
  Object
  (render [this]
    (let [current-page (:heat-page (om/props this))
          last-page (:heat-last-page (om/props this))]
      ;(log-trace "Render HeatsControll")
      ;(log "Current Page")
      ;(log current-page)
      (dom/div #js {:className "row"}
        (dom/div #js {:className "col-xs-4"}
          (dom/button #js {:className "btn btn-primary btn-block btn-lg"
                           :disabled (= current-page 0)
                           :onClick  #(om/transact! this `[(app/heat-page {:page ~(dec current-page)})
                                                           :app/heat-page])} "Previous"))
        (dom/div #js {:className "col-xs-offset-4 col-xs-4"}
          (dom/button #js {:className "btn btn-primary btn-block btn-lg"
                           :disabled  (= current-page last-page)
                           :onClick   #(om/transact! this `[(app/heat-page {:page ~(inc current-page)})
                                                            :app/heat-page])} "Next"))))))

(defui AdminComponent
  static om/IQuery
  (query [_])
  Object
  (render
    [this]
    (let [pwd (atom "")
          status (:status (om/props this))]
      (log "AdminComponent")
      (dom/div nil
        (dom/div nil
          (dom/h3 nil (str "Local Storage Says : " (:name @local-storage)))
          (dom/h3 nil (str "Complete local storage : " @local-storage))
          (dom/p nil (str "This clients id : " (:client-id @local-storage)))
          (dom/button #js {:className "btn btn-default"
                           :onClick   #(when (= @pwd "1337")
                                        (ls/clear-local-storage!))} "Clear Storage")
          (dom/input #js {:className "text" :value @pwd :onChange #(reset! pwd (.. % -target -value))})
          (dom/p nil "Refresh after clearing storage!"))
        (dom/div nil
          (dom/button #js {:className "btn btn-primary"
                           :onClick #(when (= @pwd "1337")
                                      (om/transact! this `[(app/status {:status :judging})
                                                           :app/status]))}
                      "Show Confirmed Results")
          (dom/h5 nil (str "Current State : " status)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init Client Component
(defui InitClientComponent
  static om/IQuery
  (query [_]
    [:client/id :client/name
     {:client/user
      [:adjudicator/name :adjudicator/id]}])
  Object
  (render
    [this]
    (let [client (om/props this)
          {:keys [:client/name]} client]
      (log "Client Init")
      (log (om/props this))
      (dom/div #js {:className "container"}
        (dom/h3 nil "Assign this client a name to use it as an Adjudicator device")
        (dom/div #js {:className "form-horizontal"}
          (dom/div #js {:className "form-group"}
            (dom/label #js {:className "col-sm-2 control-label"
                            :htmlFor       "clientInputName"} "Client name")
            (dom/div #js {:className "col-sm-8"}
              (dom/input #js {:className "form-control"
                              :value name
                              :id        "clientInputName"
                              :onChange #(om/transact! this `[(app/set-client-info
                                                                {:client/name ~(.. % -target -value)})])})))
          (dom/div #js {:className "form-group"}
            (dom/div #js {:className "col-sm-offset-2 col-sm-10"}
              (dom/button
                #js {:className "btn btn-default"
                     :type      "submit"
                     :onClick   #(do
                                  (let [idt (random-uuid)]
                                    (swap! local-storage assoc :client-id idt)
                                    (om/transact! this
                                                  `[(app/set-client-info {:client/id   ~idt
                                                                          :client/name ~name})
                                                    (app/status {:status :loading})
                                                    :app/status])))}
                "Connect"))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MainComponent

;https://medium.com/@kovasb/om-next-the-reconciler-af26f02a6fb4#.kwq2t2jzr
;;!!!!!!!!!
;; NOTE : Hacking around that only doing om/transact do not return a valid react component
;;  the pure state transitions should be moved to mutate fns and not pollute rendering
(defui MainComponent
  static om/IQuery
  (query [_]
    [{:app/selected-activities
      [:activity/id :activity/name
       {:activity/confirmed-by [:adjudicator/id]}
       {:activity/source [{:round/panel (om/get-query AdjudicatorSelection)}
                          :round/number-of-heats
                          :round/number-to-recall
                          {:round/starting (om/get-query HeatsComponent)}]}]}

     {:app/client (om/get-query InitClientComponent)}

     {:app/results [:result/mark-x
                    :result/point
                    :result/id
                    {:result/participant [:participant/id]}
                    {:result/adjudicator [:adjudicator/id]}
                    {:result/activity [:activity/id]}]}

     :app/heat-page
     :app/heat-page-size
     :app/admin-mode
     :app/status
     :app/local-id
     ])
  Object
  (render
    [this]
    (let [app (om/props this)
          status (:app/status app)
          selected-activity (first (:app/selected-activities (om/props this)))
          selected-round (:activity/source selected-activity)
          client (:app/client (om/props this))
          selected-adjudicator (:client/user client)

          ;;; Results must also be for selected round
          results-for-this-adjudicator (filter #(= (:adjudicator/id selected-adjudicator)
                                                   (:adjudicator/id (:result/adjudicator %)))
                                               (:app/results (om/props this)))
          mark-count (count (filter #(when (:result/mark-x %) %) results-for-this-adjudicator))
          allow-marks? (< mark-count (int (:round/number-to-recall selected-round)) )
          in-admin-mode? (:app/admin-mode (om/props this))
          confirmed? (seq (filter #(= (:adjudicator/id selected-adjudicator)
                                      (:adjudicator/id %))
                                  (:activity/confirmed-by selected-activity)))
          ]

      ;(log "Selected Adjudicator: ")
      ;(log selected-adjudicator)
      ;(log "Results :")
      ;(log (:app/results (om/props this)))
      (log "MainComponent")
      (dom/div #js {:className "container-fluid"}
        (dom/div #js {:className "col-xs-1 pull-right"}
          (dom/button #js {:className "btn btn-default"
                           :onClick   #(om/transact! this `[(app/set-admin-mode
                                                              {:in-admin ~(not in-admin-mode?)})])}
                      (dom/span #js {:className "glyphicon glyphicon-cog"})))
        (when in-admin-mode?
          (dom/div nil
            ((om/factory AdminComponent {:keyfn random-uuid}) {:status status})))
        ;(dom/div nil)
        (condp = status
          ;:loading (do
          ;           (om/transact! this `[(app/status {:status :init})])
          ;           (dom/div nil "Loading"))
          :loading (if (:client/id (:app/client app))
                     (do
                       (om/transact! this `[(app/status {:status :select-judge})])
                       (dom/p nil "Transact status"))
                     (do
                       (log (str (:client-id @local-storage)))
                       ((om/factory InitClientComponent) (:app/client (om/props this)))
                       ;(om/transact! this `[(app/status {:status :init})])
                       ))

          :init (if (:client/id (:app/client app))
                  (do
                    (om/transact! this `[(app/status {:status :select-judge})])
                    (dom/p nil "Transact status"))
                  ((om/factory InitClientComponent) (:app/client (om/props this))))

          :select-judge (if selected-adjudicator
                          (do
                            (log "Selected Adj")
                            (log selected-adjudicator)
                            (swap! local-storage assoc :adjudicator selected-adjudicator)
                            (log "Stored adj")
                            (log (:adjudicator @local-storage))
                            (om/transact! this `[(app/status {:status :waiting-for-round})])
                            (dom/p nil "Transact status"))
                          ((om/factory AdjudicatorSelection) (:app/client (om/props this))))

          :confirming (dom/div nil
                        (dom/h3 nil "Confirming results, please wait.."))

          :confirmed (do
                       (go (let [_ (<! (timeout 3000))]
                             (om/transact! this `[(app/status {:status :waiting-for-round})])
                             (dom/p nil "Transact status")))
                       (dom/div nil
                         (dom/h3 nil (str "Results have been confirmed for "
                                          (:activity/name selected-activity)))))

          :waiting-for-round (if (and selected-activity (not confirmed?))
                               (do
                                 (om/transact! this `[(app/status {:status :round-received})])
                                 (dom/p nil "Transact status"))
                               (dom/div #js {:className "container"}
                                 (dom/h3 nil (str "Judge " (:adjudicator/name selected-adjudicator)
                                                  " on client " (:client/name (:app/client app))))
                                 (when confirmed?
                                   (dom/h3 nil
                                     (str "You have confirmed the currently running round "
                                          (:activity/name selected-activity))))
                                 (dom/h3 nil "Waiting for next round..")))

          :round-received (if (and selected-activity confirmed?)
                            (do
                              (om/transact! this `[(app/status {:status :waiting-for-round})])
                              (dom/p nil "Transact status"))
                            (do
                              (om/transact! this `[(app/status {:status :judging})])
                              (dom/p nil "Transact status")))

          :judging
          (dom/div #js {:className "col-xs-12"}
            (dom/h3 #js {:className "text-center"} (str "Judge : " (:adjudicator/name
                                                                     selected-adjudicator)))

            (if selected-activity
              (dom/div nil
                (dom/h3 #js {:className "text-center"}
                        (:activity/name selected-activity))
                (dom/h3 #js {:className "text-center"}
                        (str (:round/name selected-activity)
                             " (" (:round/number-of-heats selected-round) " heats)"))
                (dom/h3 #js {:className "text-center"}
                        (str "Mark " (:round/number-to-recall selected-round) " of "
                             (count
                               (:round/starting selected-round))
                             " to next round"))

                ((om/factory HeatsComponent {:keyfn random-uuid})
                  {:participants   (:round/starting selected-round)
                   :heats          (:round/number-of-heats selected-round)
                   :adjudicator/id (:adjudicator/id
                                     selected-adjudicator)
                   :activity/id    (:activity/id selected-activity)
                   :results        results-for-this-adjudicator
                   :allow-marks?   allow-marks?
                   :heat-page-size (:app/heat-page-size (om/props this))
                   :heat-page      (:app/heat-page (om/props this))})

                (dom/div nil
                  ((om/factory HeatsControll {:keyfn random-uuid})
                    {:heat-page      (:app/heat-page (om/props this))
                     :heat-last-page (int (Math/floor
                                            (/ (int (:round/number-of-heats selected-round))
                                               (:app/heat-page-size (om/props this)))))}))

                (dom/div #js {:className "row"}
                  (dom/h1 #js {:className "col-xs-offset-4 col-xs-4 text-center"}
                          (str "Marks " mark-count "/" (:round/number-to-recall selected-round))))

                (dom/div #js {:className "row"}
                  (dom/div #js {:className "col-xs-offset-4 col-xs-4"}
                    (dom/button #js {:className "btn btn-primary btn-lg btn-block"
                                     :disabled  (not= mark-count (:round/number-to-recall selected-round))
                                     :onClick   #(om/transact!
                                                  this
                                                  `[(app/confirm-marks
                                                      ~{:results     results-for-this-adjudicator
                                                        :adjudicator selected-adjudicator
                                                        :activity    (select-keys selected-activity
                                                                                  [:activity/id])})])}
                                "Confirm Marks")))
                ))
            )
          (dom/div nil "Default"))
        )
      )))

(defn transit-post [url edn cb]
  ;(log edn)
  (.send XhrIo url
         #()                                                ;log
         ;(this-as this
         ;  (log (t/read (t/reader :json)
         ;               (.getResponseText this)))
         ;  (cb (t/read (t/reader :json) (.getResponseText this))))

         "POST" (t/write (t/writer :json) edn)
         #js {"Content-Type" "application/transit+json"}))

; http://stackoverflow.com/questions/35675766/om-nexts-query-ast-and-ast-query-functions
(defn remote-send []
  (fn [edn cb]
    (cond
      (:command edn)
      ;(log "a")
      (transit-post "/commands" edn cb)
      (:query edn)
      (go
        (let [remote-query (:query edn)
              inst (fn [s] (js/Date. s))
              response (async/<! (http/get "/query" {:query-params
                                                     {:query (pr-str remote-query)}}))
              edn-response (second (edn/read-string {:readers {'inst inst 'uuid uuid}}
                                                    (:body response)))]
          (cb edn-response))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente message handling
;(log-trace "Begin Sente message handling")

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
    (when (= topic :tx/accepted)
      (log (str "Socket Payload: " payload))
      (cond
        (= payload 'app/select-activity)
        (do
          ;; only set the activity if a judige is selected for this client
          ;(when (= (:name @local-id)
          ;         (:adjudicator/name (:payload payload))))
          (om/transact! reconciler `[;(app/selected-activities {:activities #{}})
                                     (app/status {:status :round-received})
                                     ;; TODO - consolidate with the one from main view
                                     {:app/selected-activities
                                      [:activity/id :activity/name
                                       {:activity/confirmed-by [:adjudicator/id]}
                                       {:activity/source [{:round/panel ~(om/get-query AdjudicatorSelection)}
                                                          :round/number-of-heats
                                                          :round/number-to-recall
                                                          {:round/starting ~(om/get-query HeatsComponent)}]}]}
                                     ]))

        ;; TODO - this feels ikky, should be symetrical
        (= (:topic payload) 'app/confirm-marks)
        (when (= (:adjudicator/id (:adjudicator @local-storage))
                 (:adjudicator/id (:payload payload)))
          (om/transact! reconciler `[(app/status {:status :confirmed})
                                     ;; TODO - consolidate with the one from main view
                                     {:app/selected-activities
                                      [:activity/id :activity/name
                                       {:activity/confirmed-by [:adjudicator/id]}
                                       {:activity/source [{:round/panel ~(om/get-query AdjudicatorSelection)}
                                                          :round/number-of-heats
                                                          :round/number-to-recall
                                                          {:round/starting ~(om/get-query HeatsComponent)}]}]}]))
        (= payload 'app/set-client-info)
        (do (log "Update client info")
            (om/transact! reconciler `[(app/status {:status :init})
                                       {:app/client ~(om/get-query InitClientComponent)}]))))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Socket Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;(log-trace "End Sente message handling")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(defonce app-state (atom {:app/selected-page :home
                          :app/selected-activities #{}
                          :app/heat-page 0
                          :app/heat-page-size 2
                          :app/results #{}
                          :app/admin-mode false
                          :app/status :loading
                          :app/client {:client/id (:client-id @local-storage)}}))

(def reconciler
  (om/reconciler
    {:state   app-state
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (remote-send)}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))

;(log-trace "End Application")
