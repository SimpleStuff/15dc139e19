(ns tango.cljs.adjudicator.core
  (:require-macros
    [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]

            [cognitect.transit :as t]

            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs-http.client :as http]

            [taoensso.sente :as sente :refer (cb-success?)]
            [datascript.core :as d]
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
(log-trace "Begin Sente Socket Setup")

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk"
                                  {:type :auto})]
  (def chsk chsk)
  (def ch-chsk ch-recv)                                     ; ChannelSocket's receive channel
  (def chsk-send! send-fn)                                  ; ChannelSocket's send API fn
  (def chsk-state state)                                    ; Watchable, read-only atom
  )

(log-trace "End Sente Socket Setup")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB
(log-trace "Begin Init DB")

(def adjudicator-ui-schema {:app/selected-activity {:db/cardinality :db.cardinality/one
                                                    :db/valueType :db.type/ref}

                            :app/selected-adjudicator {:db/cardinality :db.cardinality/one
                                                       :db/valueType :db.type/ref}

                            :app/results {:db/cardinality :db.cardinality/many
                                          :db/valueType :db.type/ref}

                            :result/participant {:db/cardinality :db.cardinality/one
                                                 :db/valueType :db.type/ref}

                            :result/id {:db/unique :db.unique/identity}})

(defonce conn (d/create-conn (merge adjudicator-ui-schema uidb/schema)))

(defn init-app []
  (do
    (log-trace "Init App")
    (d/transact! conn [{:db/id -1 :app/id 1}
                       {:db/id -1 :app/online? false}
                       {:db/id -1 :app/status :loading}
                       {:db/id -1 :app/selected-activity-status :in-sync}
                       {:db/id -1 :app/heat-page 0}
                       {:db/id -1 :app/heat-page-size 2}
                       {:db/id -1 :app/admin-mode false}])))

(defn app-started? [conn]
  (seq (d/q '[:find ?e
              :where
              [?e :app/id 1]] (d/db conn))))

(defn app-online? [conn]
  (d/q '[:find ?online .
         :where
         [_ :app/online? ?online]] (d/db conn)))

(log-trace "End Init DB")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Local storage
(log-trace "Begin Local Storage")

(def local-id (ls/local-storage (atom {}) :local-id))

(log-info (str "Local Adjudicator of Client : " (:name @local-id) " - "
               (:adjudicator/id (:adjudicator @local-id))))

(log-trace "End Local Storage")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente message handling
(log-trace "Begin Sente message handling")

; Dispatch on event-id
(defmulti event-msg-handler :id)

;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:keys [id ?data event] :as ev-msg}]
  (event-msg-handler {:id    (first ev-msg)
                      :?data (second ev-msg)}))

(defmethod event-msg-handler :default
  [ev-msg]
  (log-debug (str "Unhandled socket event: " ev-msg)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (do
    (log-debug (str "Channel socket state change: " ?data))
    (when (:first-open? ?data)
      (log-debug "Channel socket successfully established!"))))

;; TODO - Cleaning when respons type can be separated
(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [[topic payload] ?data]
    (when (= topic :tx/accepted)
      (log-debug (str "Socket Event from server: " topic))
      (log (str "Socket Payload: " payload))
      (when (= payload 'app/select-activity)
        (om/transact! reconciler `[(app/selected-activity-status {:status :out-of-sync})
                                   :app/selected-activity
                                   :app/results])))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log-debug (str "Socket Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

(log-trace "End Sente message handling")
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components
(log-trace "Begin Components")

(defui AdjudicatorSelection
  static om/IQuery
  (query [_]
    [:adjudicator-panel/name
     :adjudicator-panel/id
     {:adjudicator-panel/adjudicators [:adjudicator/name
                                       :adjudicator/id]}])
  Object
  (render [this]
    (log-trace "Render AdjudicatorSelection")
    (let [panel (om/props this)]
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
                                 (om/transact! this `[(app/select-adjudicator ~%)
                                                      :app/selected-adjudicator])))}
                 (:adjudicator/name %)) (:adjudicator-panel/adjudicators panel)))))))

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
      (log-trace "Render HeatRowComponent")
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
                   :disabled  true
                   ;:onClick #(if-not (and (not (:allow-marks? (om/props this)))
                   ;                         (not mark-x))
                   ;             (let [mark-value (if (or (:allow-marks? (om/props this))
                   ;                                      (and (not (:allow-marks? (om/props this)))
                   ;                                           mark-x))
                   ;                                (.. % -target -checked)
                   ;                                mark-x)]
                   ;               (om/transact!
                   ;                 reconciler
                   ;                 `[(participant/set-result
                   ;                     {:result/id          ~(if (:result/id (:result (om/props this)))
                   ;                                             (:result/id (:result (om/props this)))
                   ;                                             (random-uuid))
                   ;                      :result/mark-x      ~mark-value
                   ;                      :result/point       ~point
                   ;                      :result/participant ~(:participant/id (om/props this))
                   ;                      :result/activity    ~(:activity/id (om/props this))
                   ;                      :result/adjudicator ~(:adjudicator/id (om/props this))})
                   ;                   :app/results])))
                   }
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
                                             :result/participant ~(:participant/id (om/props this))
                                             :result/activity    ~(:activity/id (om/props this))
                                             :result/adjudicator ~(:adjudicator/id (om/props this))})
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
                                     :result/participant ~(:participant/id (om/props this))
                                     :result/activity    ~(:activity/id (om/props this))
                                     :result/adjudicator ~(:adjudicator/id (om/props this))})
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
      (log-trace "Render HeatComponent")
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
          heat-parts (partition-all (int (Math/ceil (/ (count participants) heats)))
                                    (sort-by :participant/number participants))
          page-size (:heat-page-size (om/props this))
          current-page (:heat-page (om/props this))
          page-start (* page-size current-page)
          page-end (+ page-start page-size)]
      (log-trace "Render HeatsComponent")
      (dom/div nil
        (dom/div #js {:className "col-xs-12"}
          ;(dom/h3 nil (str "Heats : " heats))
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
      (log-trace "Render HeatsControll")
      (dom/div #js {:className "row"}
        (dom/div #js {:className "col-xs-4"}
          (dom/button #js {:className "btn btn-primary btn-block btn-lg"
                           :disabled (= current-page 0)
                           :onClick  #(om/transact! this `[(app/heat-page {:page ~(dec current-page)})
                                                           :app/heat-page])} "Previous"))
        (dom/div #js {:className "col-xs-offset-4 col-xs-4"}
          (dom/button #js {:className "btn btn-primary btn-block btn-lg"
                           :disabled (= current-page last-page)
                           :onClick  #(om/transact! this `[(app/heat-page {:page ~(inc current-page)})
                                                           :app/heat-page])} "Next"))))))

;https://medium.com/@kovasb/om-next-the-reconciler-af26f02a6fb4#.kwq2t2jzr
(defui MainComponent
  static om/IQuery
  (query [_]
    [{:app/selected-activity
      [:activity/id :activity/name
       :round/recall :round/heats :round/name
       {:round/starting (om/get-query HeatsComponent)}
       {:round/panel (om/get-query AdjudicatorSelection)}]}

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
     :app/admin-mode])
  Object
  (render
    [this]
    (let [app (om/props this)
          status (:app/status app)
          selected-activity (:app/selected-activity (om/props this))
          panel (:round/panel selected-activity)
          selected-adjudicator (:app/selected-adjudicator (om/props this))
          ;; Results must also be for selected round
          results-for-this-adjudicator (filter #(= (:adjudicator/id selected-adjudicator)
                                                   (:adjudicator/id (:result/adjudicator %)))
                                               (:app/results (om/props this)))
          mark-count (count (filter #(when (:result/mark-x %) %) results-for-this-adjudicator))
          allow-marks? (< mark-count (int (:round/recall selected-activity)) )
          in-admin-mode? (:app/admin-mode (om/props this))
          ]
      (log-trace "Rendering MainComponent")

      (dom/div nil
        (dom/h3 nil (str "Selected Activity : " (:name (:app/selected-activity (om/props this)))))
        (dom/h3 nil "Adjudicator UI")
        (dom/h3 nil (str "Status : " status)))

      (dom/div #js {:className "container-fluid"}
        (when-not selected-adjudicator
          ((om/factory AdjudicatorSelection) panel))

        (when selected-adjudicator
          (dom/div nil
            (when in-admin-mode?
              (let [pwd (atom "")]
                (dom/div nil
                  (dom/h3 nil (str "Local Storage Says : " (:name @local-id)))
                  (dom/button #js {:className "btn btn-default"
                                   :onClick #(when (= @pwd "1337")
                                              (ls/clear-local-storage!))} "Clear Storage")
                  (dom/input #js {:className "text" :value @pwd :onChange #(reset! pwd (.. % -target -value))})
                  (dom/p nil "Refresh after clearing storage!"))))
            (dom/div #js {:className "col-xs-12"}

              (dom/h3 #js {:className "text-center"} (str "Judge : " (if selected-adjudicator
                                            (:adjudicator/name selected-adjudicator)
                                            "None selected")))

              (if selected-activity
                (dom/div nil
                  (dom/h3 #js {:className "text-center"} (:activity/name selected-activity))
                  (dom/h3 #js {:className "text-center"} (:round/name selected-activity))
                  (dom/h3 #js {:className "text-center"} (str "Mark " (:round/recall selected-activity) " of "
                                   (count
                                     (:round/starting selected-activity))
                                   " to next round"))
                  ((om/factory HeatsComponent) {:participants   (:round/starting selected-activity)
                                                :heats          (:round/heats selected-activity)
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
                                              (/ (int (:round/heats selected-activity))
                                                 (:app/heat-page-size (om/props this)))))}))
                  (dom/div #js {:className "row"}
                    (dom/h1 #js {:className "col-xs-offset-4 col-xs-4 text-center"}
                            (str "Marks " mark-count "/" (:round/recall selected-activity))))

                  (dom/div #js {:className "col-xs-1 pull-right"}
                    (dom/button #js {:className "btn btn-default"
                                     :onClick   #(om/transact! this `[(app/set-admin-mode
                                                                        {:in-admin ~(not in-admin-mode?)})])}
                                (dom/span #js {:className "glyphicon glyphicon-cog"}))))
                (dom/div nil
                  (dom/h3 nil "Waiting for round.."))))))))))

(log-trace "End Components")
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts
(log-trace "Begin Remote Posts")

;http://jeremyraines.com/2015/11/16/getting-started-with-clojure-web-development.html
;http://code.tutsplus.com/tutorials/mobile-first-with-bootstrap-3--net-34808
(defn transit-post [url]
  (fn [edn cb]
    (log-trace "Transit Post")
    (cond
      (:command edn) (.send XhrIo url
                           #()                              ;log  ;; TODO - Should do something with the response..
                           "POST" (t/write (t/writer :json) edn)
                           #js {"Content-Type" "application/transit+json"})
      (:query edn) (let [edn-query-str (pr-str
                                         ;; TODO - fix this hack with proper query handling
                                         (if (map? (first (:query edn)))
                                           (:query edn)
                                           (om/get-query MainComponent)))]
                     ;(log "Query >>")
                     ;(log edn)

                     (go
                       (let [response (async/<! (http/get "/query"
                                                          {:query-params
                                                           {:query edn-query-str}}))
                             body (:body response)
                             edn-result (second (cljs.reader/read-string body))]
                         ;(log "Response")
                         ;(log edn-result)
                         ;; TODO - check if cb can be used with the transaction keys and after
                         ;; doing an explicit datalog transaction
                         ;(log "App RESULT")
                         ;(log (:app/results edn-result))

                         ;; Only change round if this judge are in it
                         (let [act (:app/selected-activity edn-result)
                               adjs (:adjudicator-panel/adjudicators (:round/panel act))
                               current-adj-name (:name @local-id)
                               ;current-adj (:adjudicator @local-id)
                               should-judge? (seq (filter #(= current-adj-name (:adjudicator/name %))
                                                          adjs))
                               real-adj (first (filter #(= current-adj-name (:adjudicator/name %))
                                                       adjs))]

                           ;(log current-adj)
                           ;(log act)
                           ;(log adjs)
                           (log should-judge?)
                           (when (or should-judge? (= nil current-adj-name))
                             (om/transact! reconciler
                                           `[(app/select-activity
                                               {:activity ~(:app/selected-activity edn-result)})
                                             (app/set-results
                                               {:results ~(:app/results edn-result)})
                                             (app/heat-page ~{:page 0})
                                             ]))

                           ;; Always set judge to the locally selected
                           (log "Real Adj")
                           (log real-adj)
                           (if real-adj
                             (om/transact! reconciler `[(app/select-adjudicator ~real-adj)
                                                        :app/selected-adjudicator]))
                           ;(om/transact! reconciler `[(app/select-adjudicator ~current-adj)
                           ;                           :app/selected-adjudicator])
                           )))))))

(log-trace "End Remote Posts")
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(log-trace "Begin Application")

;; Init db etc if it has not been done
(when-not (app-started? conn)
  (init-app))

(def reconciler
  (om/reconciler
    {:state   conn
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (transit-post "/commands")}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))

(log-trace "End Application")