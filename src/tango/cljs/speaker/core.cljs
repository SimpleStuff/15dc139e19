(ns tango.cljs.speaker.core
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
            [tango.cljs.speaker.mutation :as m]
            [tango.cljs.speaker.read :as r]
            [alandipert.storage-atom :as ls])

  (:import [goog.net XhrIo]))

(defn log [m]
  (.log js/console m))

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
    (when (= topic :tx/accepted)
      (log (str "Socket Event from server: " topic))
      (log (str "Socket Payload: " payload))
      (cond
        (= payload 'app/select-activity)
        (log "select")

        (= (:topic payload) 'app/confirm-marks)
        (log "confirm")))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Socket Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; MainComponent

;; Activity name       Round 1
;; type? (medium)

;; Heat 1: 1880
;; Heat 2: 1888

;; Total 31
;; Recall 18
;; Judges A, B, C      Event 3A

(defui HeatRow
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [{:keys [heat-number starting]} (om/props this)]
      (dom/div #js {:className "Row"}
        (dom/h4 #js {:className "col-xs-2"} (str "Heat " heat-number ":"))
        (dom/div #js {:className "col-xs-10"}
          (map #(dom/h4 #js {:className "col-xs-1"} (:participant/number %)) starting))))))

(defui HeatsComponent
  static om/IQuery
  (query [_]
    [])
  Object
  (render
    [this]
    (let [{:keys [heats starting]} (om/props this)
          heat-dist (domain/create-distribution (sort-by :participant/number starting) heats)]
      (log heat-dist)
      (dom/div nil
        (map-indexed #((om/factory HeatRow) {:heat-number (inc %1)
                                             :starting    %2})
                     heat-dist)))))

(defui ActivityComponent
  static om/IQuery
  (query [_]
    [:activity/name
     :activity/number
     :activity/position
     :round/recall
     :round/heats
     :round/index
     {:round/speaker-dances [:dance/name]}
     {:round/starting [:participant/number]}
     {:round/panel [{:adjudicator-panel/adjudicators [:adjudicator/name]}]}])
  Object
  (render
    [this]
    (let [activity (om/props this)
          panel (:adjudicator-panel/adjudicators (:round/panel (om/props this)))
          starting (:round/starting activity)]
      (dom/div nil
        (dom/div #js {:className "Row"}

          (dom/h1 #js {:className "col-xs-8"} (:activity/name activity))

          (dom/h1 #js {:className "col-xs-offset-1 col-xs-3 pull-right"}
                  (str "Round " (inc (:round/index activity)))))
        (dom/div #js {:className "Row"}
          (dom/h4 #js {:className "col-xs-12"} (clojure.string/join "," (map :dance/name (:round/speaker-dances activity)))))

        ;; TODO - fix heats
        ((om/factory HeatsComponent) {:heats (:round/heats activity)
                                      :starting (:round/starting activity)})

        (dom/div #js {:className "Row"}
          (dom/h3 #js {:className "col-xs-12"} (str "Total: " (count starting))))

        (dom/div #js {:className "Row"}
          (dom/h3 #js {:className "col-xs-12"} (str "Recall: " (:round/recall (om/props this)))))

        (dom/div #js {:className "Row"}
          (dom/h3 #js {:className "col-xs-8 "}
                  (str "Judges: "
                       (clojure.string/join "," (map :adjudicator/name panel))))

          (dom/h3 #js {:className "col-xs-offset-2 col-xs-2 pull-right"}
                  (str "Event: " (:activity/number activity))))))))

(defui MainComponent
  static om/IQuery
  (query [_]
    [{:app/speaker-activites (om/get-query ActivityComponent)}])
  Object
  (render
    [this]
    (let [activites (:app/speaker-activites (om/props this))]
      (dom/div #js {:className "container-fluid"}
        (map #((om/factory ActivityComponent) %)
             (sort-by :activity/position activites))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote com
(defn remote-send []
  (fn [edn cb]
    (log "Remote Send")
    (log edn)
    (cond
      (:query edn)
      (go
        (let [response (async/<! (http/get "/query" {:query-params
                                                     {:query (pr-str (:query edn))}}))
              edn-response (second (cljs.reader/read-string (:body response)))]
          ;(log edn-response)
          ;; TODO - why is the response a vec?
          (cb edn-response))
        ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application
(defonce app-state (atom {:app/speaker-activites [{:activity/name "Test"}]}))

(def reconciler
  (om/reconciler
    {:state   app-state
     :remotes [:command :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (remote-send)}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))