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
                                                    :db/valueType :db.type/ref}})

(defonce conn (d/create-conn (merge adjudicator-ui-schema uidb/schema)))

;(defonce conn (d/create-conn adjudicator-ui-schema))

(defn init-app []
  (d/transact! conn [{:db/id -1 :app/id 1}
                     {:db/id -1 :app/online? false}
                     ;{:db/id -1 :selected-page :competitions}
                     ;{:db/id -1 :app/import-status :none}
                     {:db/id -1 :app/status :running}
                     ;{:db/id -1 :app/selected-competition {}}

                     {:db/id -1 :app/selected-activity-status :in-sync}
                     ]))

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
      (log payload)
      (om/transact! reconciler `[(app/selected-activity-status {:status :out-of-sync})
                                 :app/selected-activity]))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Handshake: " ?data))))

(defonce chsk-router
         (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components


;; TODO:
;; Data needed from the back end:
; - Judge info
; - Round name
; - Participants to be marked

;; TODO:
;; - Select judge for UI
;; X Get selected round that is to be judged
;; - Get the number of participants to be recalled
;; - Show participant number
;; - Command to set mark on participant for the specific round for this judge
;; - Command to inc/dec the judges 'point' for a participant
(defui MainComponent
  static om/IQuery
  (query [_]
    [{:app/selected-activity
      [:activity/id :activity/name
       :round/recall :round/heats :round/name
       {:round/starting [:participant/number :participant/id]}]}])
  Object
  (render
    [this]
    (let [app (om/props this)
          status (:app/status app)
          next-status (if (not= status :on) :on :off)
          selected-activity (:app/selected-activity (om/props this))]
      ;(log app)
      (log "Rendering MainComponent")
      (dom/div nil
        (dom/h3 nil (str "Selected Activity : " (:name (:app/selected-activity (om/props this)))))
        (dom/h3 nil "Adjudicator UI")
        (dom/h3 nil (str "Status : " status)))

      (dom/div nil
        (dom/h3 nil (str "Judge : " "TODO"))
        (dom/h3 nil (:activity/name selected-activity))
        (dom/h3 nil (:round/name selected-activity))
        (dom/h3 nil (str "Mark " (:round/recall selected-activity) " of "
                         (count
                           (:round/starting selected-activity))))
        (dom/h3 nil (str "Heats TODO : " (:round/heats selected-activity)))
        (dom/h3 nil (str "Example Participant " (:participant/number (first (:round/starting selected-activity)))))))))

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts

;http://jeremyraines.com/2015/11/16/getting-started-with-clojure-web-development.html


;; example of a mark message
;; [:set-result {:round/id 1 :adjudicator/id 1 :participant-id 1 :mark/x true}]
(defn transit-post [url]
  (fn [edn cb]
    (log edn)
    (cond
      (:remote edn) (.send XhrIo url
                           log
                           ;; TODO - Should do something with the response..
                           ;(this-as this
                           ;  (log (t/read (t/reader :json)
                           ;               (.getResponseText this)))
                           ;  (cb (t/read (t/reader :json) (.getResponseText this))))

                           "POST" (t/write (t/writer :json) edn)
                           #js {"Content-Type" "application/transit+json"})
      (:query edn) (let [edn-query-str (pr-str (om/get-query MainComponent))]
                     (log (str "Run Query: " (pr-str (:query edn))))
                     (log (:query edn))

                     (log (str "Om Query" (pr-str (om/get-query MainComponent))))
                     ;; TODO - testa om/get-query on component

                     (go
                       (let [response (async/<! (http/get "http://localhost:1337/query"
                                                          {:query-params {:query edn-query-str}}))
                             body (:body response)
                             edn-result (second (cljs.reader/read-string body))]
                         (log "Response")
                         (log (:body response))
                         (log "Edn")
                         (log edn-result)
                         ;(om/transact! reconciler `[(app/status {:status "uff"})])
                         ;(log (second (cljs.reader/read-string (:body response))))
                         ;(om/transact! reconciler `[(app/select-activity
                         ;                             {:name ~(:activity/name
                         ;                                       (:app/selected-activity
                         ;                                         edn-result))})])
                         (om/transact! reconciler `[(app/select-activity
                                                      {:activity ~(:app/selected-activity edn-result)})
                                                    ;:app/selected-activity
                                                    ])
                         ))))))

;[{:app/selected-activity [:activity/id :activity/name
;                          :round/recall :round/heats
;                          :round/name {:round/starting [:participant/number]}]}]

;(defn sente-post []
;  (fn [{:keys [remote] :as env} cb]
;    (do
;      (log "Env > ")
;      (log env)
;      (log (str "Sent to Tango Backend => " remote))
;      (log (http/post
;             "http://localhost:1337/commands"
;             {:query-params {:command remote}}
;             ;{:json-params {:command remote}}
;             ;{:form-params {:command remote}}
;             ;{:edn-params {:command remote}}
;             ))
;
;      ;(chsk-send! [:event-manager/query [[:competition/name :competition/location]]])
;      )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

;; Init db etc if it has not been done
(when-not (app-started? conn)
  (init-app))

(def reconciler
  (om/reconciler
    {:state   conn
     :remotes [:remote :query]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (transit-post "http://localhost:1337/commands")}))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))