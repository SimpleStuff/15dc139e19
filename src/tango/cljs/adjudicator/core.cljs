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
            [tango.cljs.adjudicator.read :as r])
  (:import [goog.net XhrIo]))

(defn log [m]
  (.log js/console m))

(declare reconciler)
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
;; Init DB
(def adjudicator-ui-schema {:app/selected-activity {:db/cardinality :db.cardinality/one
                                                    :db/valueType :db.type/ref}

                            :app/selected-adjudicator {:db/cardinality :db.cardinality/one
                                                       :db/valueType :db.type/ref}

                            :app/results {:db/cardinality :db.cardinality/many
                                          :db/valueType :db.type/ref}

                            :result/participant {:db/cardinality :db.cardinality/one
                                                 :db/valueType :db.type/ref}

                            :result/id {:db/unique :db.unique/identity}

                            ;:result/point {:db/valueType :db.type/long}
                            })

(defonce conn (d/create-conn (merge adjudicator-ui-schema uidb/schema)))

;(defonce conn (d/create-conn adjudicator-ui-schema))

(defn init-app []
  (do
    (d/transact! conn [{:db/id -1 :app/id 1}
                       {:db/id -1 :app/online? false}
                       ;{:db/id -1 :selected-page :competitions}
                       ;{:db/id -1 :app/import-status :none}
                       {:db/id -1 :app/status :loading}
                       ;{:db/id -1 :app/selected-competition {}}
                       ; :app/selected-adjudicator
                       {:db/id -1 :app/selected-activity-status :in-sync}
                       {:db/id -1 :app/heat-page 0}
                       {:db/id -1 :app/heat-page-size 2}
                       ;{:db/id -1 :app/results {}}
                       ])
    (let [r (d/q '[:find ?s .
                   :where [[:app/id 1] :app/status ?s]]
                 (d/db conn))]
      (log (str "Empty Results> " r)))
    ))



(defn app-started? [conn]
  (seq (d/q '[:find ?e
              :where
              [?e :app/id 1]] (d/db conn))))

(defn app-online? [conn]
  (d/q '[:find ?online .
         :where
         [_ :app/online? ?online]] (d/db conn)))

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
  (log (str "Unhandled event: " ev-msg)))

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
      (log (str " event from server: " topic))
      (log (str "payload: " payload))
      (when (= payload 'app/select-activity)
        (om/transact! reconciler `[(app/selected-activity-status {:status :out-of-sync})
                                   (app/select-adjudicator {})
                                   :app/selected-activity
                                   :app/results])))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components



;; TODO:
;; X Select judge for UI
;; X Get selected round that is to be judged
;; X Get the number of participants to be recalled
;; X Show participant number
;; - Command to set mark on participant for the specific round for this judge
;; - Command to inc/dec the judges 'point' for a participant
;; - Query param to only fetch results for the selected adjudicator
(defui AdjudicatorSelection
  static om/IQuery
  (query [_]
    [:adjudicator-panel/name
     :adjudicator-panel/id
     {:adjudicator-panel/adjudicators [:adjudicator/name
                                       :adjudicator/id]}])
  Object
  (render [this]
    ;(log "Render Panels")
    (let [panel (om/props this)]
      ;(log panel)
      (dom/div nil
        (dom/h3 nil (str "Panel for this round : " (:adjudicator-panel/name panel)))
        (dom/h3 nil (str "Select judge for use in this client :"))
        (dom/ul nil
          (map #(dom/li #js {:onClick
                             (fn [e]
                               (do
                                 ;(log (str "Click " (:adjudicator/name %)))
                                 (om/transact! this `[(app/select-adjudicator ~%)
                                                      :app/selected-adjudicator])))}
                 (:adjudicator/name %)) (:adjudicator-panel/adjudicators panel)))))))

;; example of a mark message
;; [:set-result {:round/id 1 :adjudicator/id 1 :participant-id 1 :mark/x true}]

;{:round/results
; [{:result/id 1 :result/adjudicator 2 :result/participant 3 :result/mark-x}]}
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
      ;(log "Render row")
      ;(when (:result/id (:result (om/props this))))

      ;(log "----")
      (dom/div nil
        (dom/form #js {:className "form-inline"}
          (dom/div #js {:className "form-group"}
            (dom/p #js {:className "control-label"} (str (:participant/number (om/props this)))))

          ;; TODO - change on + and - should also send a result
          (dom/div #js {:className "form-group"}
            (dom/label nil
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
                                              :app/results])
                                          )})))

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
            (dom/div #js {:className "form-group"}
              (dom/button #js {:type      "button"
                               :className "btn btn-default"
                               :onClick   #(set-result-fn inc)} "+")

              (dom/button #js {:type "button"
                               :className "btn btn-default"
                               :onClick #(set-result-fn dec)} "-")

              (when (not= 0 point)
                (dom/label #js {:className "control-label"} (str point))))))))))

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
      (dom/div #js {:className "col-sm-6"}
        (dom/h3 nil "Heat : " (str (+ 1 heat)))
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
      (dom/div nil
        (dom/div #js {:className "col-sm-12"}
          (dom/h3 nil (str "Heats : " heats))
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
      (dom/div nil
        (dom/button #js {:disabled (= current-page 0)
                         :onClick #(om/transact! this `[(app/heat-page {:page ~(dec current-page)})
                                                        :app/heat-page])} "Previous")
        (dom/button #js {:disabled (= current-page last-page)
                         :onClick #(om/transact! this `[(app/heat-page {:page ~(inc current-page)})
                                                        :app/heat-page])} "Next")))))

;{:round/results
; [{:result/id 1 :result/adjudicator 2 :result/participant 3 :result/mark-x}]}

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
     :app/heat-page-size])
  Object
  (render
    [this]
    (let [app (om/props this)
          status (:app/status app)
          selected-activity (:app/selected-activity (om/props this))
          panel (:round/panel selected-activity)
          selected-adjudicator (:app/selected-adjudicator (om/props this))
          results-for-this-adjudicator (filter #(= (:adjudicator/id selected-adjudicator)
                                                   (:adjudicator/id (:result/adjudicator %)))
                                               (:app/results (om/props this)))
          mark-count (count (filter #(when (:result/mark-x %) %) results-for-this-adjudicator))
          allow-marks? (< mark-count (int (:round/recall selected-activity)) )
          ]
      (log "Rendering MainComponent")
      ;(log "Marks--------------------------------")
      ;(log allow-marks?)
      ;(log (int (:round/recall selected-activity)))
      ;(log mark-count)
      ;(log (:app/results (om/props this)))
      ;(log app)
      ;(log "--------------------------------------------")
      ;(log (str selected-adjudicator))
      (dom/div nil
        (dom/h3 nil (str "Selected Activity : " (:name (:app/selected-activity (om/props this)))))
        (dom/h3 nil "Adjudicator UI")
        (dom/h3 nil (str "Status : " status)))

      (dom/div nil
        ((om/factory AdjudicatorSelection) panel)

        (when selected-adjudicator
          (dom/div nil
            (dom/div nil
              (dom/h3 nil (str "Judge : " (if selected-adjudicator
                                            (:adjudicator/name selected-adjudicator)
                                            "None selected")))
              (dom/h3 nil (:activity/name selected-activity))
              (dom/h3 nil (:round/name selected-activity))
              (dom/h3 nil (str "Mark " (:round/recall selected-activity) " of "
                               (count
                                 (:round/starting selected-activity))
                               " to next round"))
              ((om/factory HeatsComponent) {:participants   (:round/starting selected-activity)
                                            :heats          (:round/heats selected-activity)
                                            :adjudicator/id (:adjudicator/id selected-adjudicator)
                                            :activity/id    (:activity/id selected-activity)
                                            :results        results-for-this-adjudicator
                                            :allow-marks? allow-marks?
                                            :heat-page-size (:app/heat-page-size (om/props this))
                                            :heat-page (:app/heat-page (om/props this))})
              )
            (dom/div nil
              ((om/factory HeatsControll)
                {:heat-page      (:app/heat-page (om/props this))
                 :heat-last-page (int (Math/floor
                                        (/ (int (:round/heats selected-activity))
                                           (:app/heat-page-size (om/props this)))))}))
            (dom/div nil
              (dom/h3 nil (str "Marks " mark-count "/" (:round/recall selected-activity))))

            ))
        ;(dom/h3 nil (str "Example Participant " (:participant/number
        ;                                          (first (:round/starting selected-activity)))))
        ))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts

;http://jeremyraines.com/2015/11/16/getting-started-with-clojure-web-development.html

;; example of a mark message
;; [:set-result {:round/id 1 :adjudicator/id 1 :participant-id 1 :mark/x true}]
(defn transit-post [url]
  (fn [edn cb]
    ;(log edn)
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
                     (log "Query >>")
                     (log edn)

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
                         (om/transact! reconciler
                                       `[(app/select-activity
                                           {:activity ~(:app/selected-activity edn-result)})
                                         (app/set-results
                                           {:results ~(:app/results edn-result)})])))))))

; {:query [:app/selected-activity :app/results]}
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test components
;; Command Test
;(dom/span nil
;  (dom/label nil "Command Test : ")
;  (dom/button #js {:onClick
;                   #(do
;                     (om/transact!
;                       this
;                       `[(app/status {:status ~next-status})
;                         (app/online? {:online? true})])
;                     (log "Command"))} "Command"))

;; Query test
;(dom/span nil
;  (dom/label nil "Query Test : ")
;  (dom/button #js {:onClick #(do
;                              (log "Query")
;                              (.send XhrIo "http://localhost:1337/query?a"
;                                     (fn [e]
;                                       (do
;                                         (log "Query CB")
;                                         (log e)))
;                                     "GET"
;                                     "Test"))}
;              "Query"))

;; Query 2 test
;(dom/span nil
;  (dom/label nil "Query Test 2: ")
;  (dom/button #js {:onClick #(do
;                              (log "Query")
;                              (http/get "http://localhost:1337/query"
;                                        {:query-params {:query (pr-str '[:find])}}))}
;              "Query 2"))
