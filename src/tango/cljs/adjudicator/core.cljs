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

            [tango.ui-db :as uidb]
            [tango.domain :as domain]
            [tango.presentation :as presentation]
            [tango.cljs.adjudicator.mutation :as m]
            [tango.cljs.adjudicator.read :as r]
            [alandipert.storage-atom :as ls])
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

(def local-id (ls/local-storage (atom {}) :local-id))

;(log-info (str "Local Adjudicator of Client : " (:name @local-id) " - "
;               (:adjudicator/id (:adjudicator @local-id))))

;(log-trace "End Local Storage")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB
;(log-trace "Begin Init DB")

;(def adjudicator-ui-schema {:app/selected-activity {:db/cardinality :db.cardinality/one
;                                                    :db/valueType :db.type/ref}
;
;                            :app/selected-adjudicator {:db/cardinality :db.cardinality/one
;                                                       :db/valueType :db.type/ref}
;
;                            :app/results {:db/cardinality :db.cardinality/many
;                                          :db/valueType :db.type/ref}
;
;                            :result/participant {:db/cardinality :db.cardinality/one
;                                                 :db/valueType :db.type/ref}
;
;                            :result/id {:db/unique :db.unique/identity}})
;
;(defonce conn (d/create-conn (merge adjudicator-ui-schema uidb/schema)))

;(defn init-app []
;  (do
;    ;(log-trace "Init App")
;    (d/transact! conn [{:db/id -1 :app/id 1}
;                       {:db/id -1 :app/online? false}
;                       {:db/id -1 :app/status (if (:app/status @local-id)
;                                                (:app/status @local-id)
;                                                :judging)}
;                       {:db/id -1 :app/selected-activity-status :in-sync}
;                       {:db/id -1 :app/heat-page 0}
;                       {:db/id -1 :app/heat-page-size 2}
;                       {:db/id -1 :app/admin-mode false}])))

;(defn app-started? [conn]
;  (seq (d/q '[:find ?e
;              :where
;              [?e :app/id 1]] (d/db conn))))
;
;(defn app-online? [conn]
;  (d/q '[:find ?online .
;         :where
;         [_ :app/online? ?online]] (d/db conn)))

;(log-trace "End Init DB")

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
      ;(log (str "Socket Payload: " payload))
      (cond
        (= payload 'app/select-activity)
        (do
          (om/transact! reconciler `[(app/selected-activity-status {:status :out-of-sync})
                                     ;(app/status {:status :judging})
                                     ;:app/status
                                     :app/results]))

        (= (:topic payload) 'app/confirm-marks)
        (when (= (:name @local-id)
                 (:adjudicator/name (:payload payload)))
          ;; TODO - only confirm if it was this client
          (om/transact! reconciler `[(app/status {:status :confirmed})]))))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Socket Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;(log-trace "End Sente message handling")
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
    ;(log-trace "Render AdjudicatorSelection")
    (let [panel (om/props this)]
      (dom/div nil
        (if (:adjudicator-panel/name panel)
          (dom/div nil
            (dom/h3 nil (str "Panel for this round : " (:adjudicator-panel/name panel)))
            (dom/h3 nil (str "Select judge for use in this client :"))
            (dom/ul nil
              (map #(dom/li #js {:onClick
                                 (fn [e]
                                   (do
                                     ;; Persist this adjudicator to storage
                                     (swap! local-id assoc :name (:adjudicator/name %))
                                     ;(swap! local-id assoc :adjudicator %)
                                     (om/transact! this `[(app/select-adjudicator {:adjudicator ~%})
                                                          :app/selected-adjudicator])))}
                     (:adjudicator/name %)) (:adjudicator-panel/adjudicators panel))))
          (dom/div nil
            (dom/h3 nil "Waiting for Adjudicator Panel to select Adjudicator from..")))))))

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
      ;(log "Render HeatRowComponent")
      ;(log "Results")
      ;(log result)
      ;(log "Props")
      ;(log (om/props this))
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
      ;(log "Render HeatComponent")
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
             participants)))))

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

      (dom/div nil
        (dom/div #js {:className "col-xs-12"}
          (subvec
            (vec (map-indexed (fn [idx parts] ((om/factory HeatComponent)
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

;https://medium.com/@kovasb/om-next-the-reconciler-af26f02a6fb4#.kwq2t2jzr
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

     {:app/selected-adjudicator [:adjudicator/name
                                 :adjudicator/id]}

     {:app/results [:result/mark-x
                    :result/point
                    :result/id
                    {:result/participant [:participant/id]}
                    {:result/adjudicator [:adjudicator/id]}
                    {:result/activity [:activity/id]}]}

     :app/heat-page
     :app/heat-page-size
     ;:app/admin-mode
     ;:app/status
     ])
  Object
  (render
    [this]
    (let [app (om/props this)
          ;status (:app/status app)
          selected-activity (first (:app/selected-activities (om/props this)))
          selected-round (:activity/source selected-activity)
          panel (:round/panel selected-round)
          selected-adjudicator (:app/selected-adjudicator (om/props this))
          ;;; Results must also be for selected round
          results-for-this-adjudicator (filter #(= (:adjudicator/id selected-adjudicator)
                                                   (:adjudicator/id (:result/adjudicator %)))
                                               (:app/results (om/props this)))
          ;results-for-this-adjudicator (filter #(= (:adjudicator/id selected-adjudicator)
          ;                                         (:result/adjudicator %))
          ;                                     (:app/results (om/props this)))
          mark-count (count (filter #(when (:result/mark-x %) %) results-for-this-adjudicator))
          allow-marks? (< mark-count (int (:round/number-to-recall selected-round)) )
          ;in-admin-mode? (:app/admin-mode (om/props this))
          ]
      ;(log-trace "Rendering MainComponent")
      ;(log selected-activity)
      ;(log "Selected Adjudicator")
      ;(log selected-adjudicator)
      ;(log "Results for adj")
      ;(log results-for-this-adjudicator)
      ;(log (:app/results (om/props this)))
      (dom/div #js {:className "container-fluid"}
        (when-not selected-adjudicator
          ((om/factory AdjudicatorSelection) panel))
        (when selected-adjudicator
          (dom/div #js {:className "col-xs-12"}
            (dom/h3 #js {:className "text-center"} (str "Judge : "
                                                        (if selected-adjudicator
                                                          (:adjudicator/name selected-adjudicator)
                                                          "None selected")))

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

                ((om/factory HeatsComponent) {:participants   (:round/starting selected-round)
                                              :heats          (:round/number-of-heats selected-round)
                                              :adjudicator/id (:adjudicator/id selected-adjudicator)
                                              :activity/id    (:activity/id selected-activity)
                                              :results        results-for-this-adjudicator
                                              :allow-marks?   allow-marks?
                                              :heat-page-size (:app/heat-page-size (om/props this))
                                              :heat-page      (:app/heat-page (om/props this))})

                (dom/div nil
                  ((om/factory HeatsControll)
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
                                                        :activity (select-keys selected-activity
                                                                               [:activity/id])})])}
                                "Confirm Marks")))
                ))
            ))
        )
      )))

;(log-trace "End Components")
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts
;(log-trace "Begin Remote Posts")

;http://jeremyraines.com/2015/11/16/getting-started-with-clojure-web-development.html
;http://code.tutsplus.com/tutorials/mobile-first-with-bootstrap-3--net-34808
;(defn transit-post [url]
;  (fn [edn cb]
;    ;(log-trace "Transit Post")
;    (cond
;      (:command edn) (.send XhrIo url
;                           #()                              ;log  ;; TODO - Should do something with the response..
;                           "POST" (t/write (t/writer :json) edn)
;                           #js {"Content-Type" "application/transit+json"})
;      (:query edn) (let [edn-query-str (pr-str
;                                         ;; TODO - fix this hack with proper query handling
;                                         (if (map? (first (:query edn)))
;                                           (:query edn)
;                                           (om/get-query MainComponent)))]
;                     ;(log "Query >>")
;                     ;(log edn)
;
;                     (go
;                       (let [response (async/<! (http/get "/query"
;                                                          {:query-params
;                                                           {:query edn-query-str}}))
;                             body (:body response)
;                             edn-result (second (cljs.reader/read-string body))
;                             all-act (:app/selected-activity edn-result)
;                             acts-for-this-adj (filter (fn [act]
;                                                         (seq (filter #(= (:name @local-id) (:adjudicator/name %))
;                                                                      (:adjudicator-panel/adjudicators (:round/panel act)))))
;                                                       all-act)
;                             awsome-act (first acts-for-this-adj)]
;
;                         (when (not= (count acts-for-this-adj) 1)
;                           (log "WARNING MULTIPLE RUNNING ACTIVITIES"))
;                         ;(log "Local Judge")
;                         ;(log (:name @local-id))
;                         ;(log "Response")
;                         ;(log awsome-act)
;                         ;; TODO - ska det vara en lista med ett element kanske?
;
;
;                         ;; TODO - check if cb can be used with the transaction keys and after
;                         ;; doing an explicit datalog transaction
;                         ;(log "App RESULT")
;                         ;(log (:app/results edn-result))
;
;                         ;; Only change round if this judge are in it
;                         (let [adjs (:adjudicator-panel/adjudicators (:round/panel awsome-act))
;                               current-adj-name (:name @local-id)
;                               should-judge? (seq (filter #(= current-adj-name (:adjudicator/name %))
;                                                          adjs))
;                               real-adj (first (filter #(= current-adj-name (:adjudicator/name %))
;                                                       adjs))]
;
;                           ;(log "Should judge")
;                           ;(log should-judge?)
;
;                           ;(log "Results")
;                           ;(log (:app/results edn-result))
;                           ;; TODO - need to make better handling of client adjudicator
;                           ;; configuration
;                           (when (or should-judge? (= nil current-adj-name))
;                             (let [act-to-change-to (if (= nil current-adj-name)
;                                                      (first all-act)
;                                                      awsome-act)
;                                   results-for-this-adj
;                                   (filter #(= (:adjudicator/id real-adj)
;                                               (:adjudicator/id (:result/adjudicator %)))
;                                           (:app/results edn-result))]
;
;                               ;; Time to judge another round, reset local db from previous round
;                               ;;  and set the new round as selected
;                               (log "Pre Reset")
;
;
;                               (log "Pos Reset")
;                               (om/transact! reconciler
;                                             `[(app/select-activity
;                                                 {:activity ~act-to-change-to})
;                                               (app/set-results
;                                                 {:results ~results-for-this-adj})
;                                               (app/heat-page ~{:page 0})
;                                               (app/status ~{:status :judging})
;                                               ])))
;
;                           ;; Always set judge to the locally selected
;                           ;(log "Real Adj")
;                           ;(log real-adj)
;                           (if real-adj
;                             (om/transact! reconciler `[(app/select-adjudicator ~real-adj)
;                                                        :app/selected-adjudicator]))
;                           ;(om/transact! reconciler `[(app/select-adjudicator ~current-adj)
;                           ;                           :app/selected-adjudicator])
;                           )))))))

;(log-trace "End Remote Posts")

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

(defn remote-send []
  (fn [edn cb]
    (cond
      (:command edn)
      ;(log "a")
      (transit-post "/commands" edn cb)
      (:query edn)
      (go
        ;(log "Query")
        ;(log edn)
        (let [remote-query (if (map? (first (:query edn)))
                             (:query edn)
                             ;; TODO - why do we not get a good query
                             (conj [] (first (om/get-query MainComponent))))
              response (async/<! (http/get "/query" {:query-params
                                                     {:query (pr-str remote-query)}}))
              edn-response (second (cljs.reader/read-string (:body response)))]

          (log remote-query)
          ;; TODO - why is the response a vec?
          (cb edn-response)
          )))))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(defonce app-state (atom {:app/selected-page :home
                          :app/selected-activities #{}
                          :app/selected-adjudicator nil
                          :app/heat-page 0
                          :app/heat-page-size 2
                          :app/results #{}
                          :app/status :loading}))

(def reconciler
  (om/reconciler
    {:state   app-state
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (remote-send)}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))

;(log-trace "End Application")
