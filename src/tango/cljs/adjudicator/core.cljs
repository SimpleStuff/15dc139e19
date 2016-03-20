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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB
(defonce conn (d/create-conn uidb/schema))

(defn init-app []
  (d/transact! conn [{:db/id -1 :app/id 1}
                     {:db/id -1 :app/online? false}
                     ;{:db/id -1 :selected-page :competitions}
                     ;{:db/id -1 :app/import-status :none}
                     {:db/id -1 :app/status :running}
                     ;{:db/id -1 :app/selected-competition {}}
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
;; Components


;; TODO:
;; Data needed from the back end:
; - Judge info
; - Round name
; - Participants to be marked

;; TODO:
;; - Select judge for UI
;; - Get selected round that is to be judged
;; - Get the number of participants to be recalled
;; - Show participant number
;; - Command to set mark on participant for the specific round for this judge
;; - Command to inc/dec the judges 'point' for a participant
(defui MainComponent
  static om/IQuery
  (query [_]
    [:app/status
     {:app/selected-activity [:activity/name]}])
  Object
  (render
    [this]
    (let [app (om/props this)
          status (:app/status app)
          next-status (if (not= status :on) :on :off)]
      ;(log status)
      (dom/div nil
        (dom/h3 nil (str "Selected Activity : " (:name (:app/selected-activity (om/props this)))))
        (dom/h3 nil "Adjudicator UI")
        (dom/h3 nil (str "Status : " status))
        (dom/span nil
          (dom/label nil "Command Test : ")
                  ;https://github.com/r0man/cljs-http
          (dom/button #js {:onClick
                           #(do
                             (om/transact!
                               this
                               `[(app/status {:status ~next-status})
                                 (app/online? {:online? true})])
                             ;(log (http/post
                             ;       "http://localhost:1337/commands"
                             ;       {:form-params {:foo :bar}}
                             ;       ;{:edn-params {:foo :bar}}
                             ;       ))
                             (log "Command"))} "Command"))

        (dom/span nil
          (dom/label nil "Query Test : ")
          (dom/button #js {:onClick #(do
                                      (log "Query")
                                      (.send XhrIo "http://localhost:1337/query?a"
                                             (fn [e]
                                               (do
                                                 (log "Query CB")
                                                 (log e)))
                                             "GET"
                                             "Test"))}
                      "Query"
            ))

        (dom/span nil
          (dom/label nil "Query Test 2: ")
          (dom/button #js {:onClick #(do
                                      (log "Query")
                                      (http/get "http://localhost:1337/query"
                                                {:query-params {:query (pr-str '[:find])}}))}
                      "Query 2"
                      ))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts

;http://jeremyraines.com/2015/11/16/getting-started-with-clojure-web-development.html

(declare reconciler)
;; example of a mark message
;; [:set-result {:round/id 1 :adjudicator/id 1 :participant-id 1 :mark/x true}]
(defn transit-post [url]
  (fn [edn cb]
    (log edn)
    (cond
      (:remote edn) (.send XhrIo url
                           (fn [e]
                             (log e)
                             ;(this-as this
                             ;  (log (t/read (t/reader :json)
                             ;               (.getResponseText this)))
                             ;  (cb (t/read (t/reader :json) (.getResponseText this))))
                             )
                           "POST" (t/write (t/writer :json) edn)
                           #js {"Content-Type" "application/transit+json"})
      (:query edn) (do
                     (log "Queryzz")
                     ;(http/get "http://localhost:1337/query"
                     ;          {:query-params {:query (pr-str (:query edn))}})
                     (go
                       (let [response (async/<! (http/get "http://localhost:1337/query"
                                                          {:query-params {:query (pr-str (:query edn))}}))]
                         (log "Response")
                         (log (:body response))
                         ;(om/transact! reconciler `[(app/add-competition ~clean-data) :app/competitions])
                         (om/transact! reconciler `[(app/status {:status "uff"})])
                         (log (second (cljs.reader/read-string (:body response))))
                         ;(cb (second (cljs.reader/read-string (:body response))))
                         (cb {:app/status {:app/selected-activity {:activity/name "Doo"}}})
                         ;(cb [:app/status {:app/selected-activity [:activity/name]}])
                         (log (om/app-state reconciler))
                         ))
                     ))))

;[:app/status
; {:app/selected-activity [:activity/name]}]

(defn sente-post []
  (fn [{:keys [remote] :as env} cb]
    (do
      (log "Env > ")
      (log env)
      (log (str "Sent to Tango Backend => " remote))
      (log (http/post
             "http://localhost:1337/commands"
             {:query-params {:command remote}}
             ;{:json-params {:command remote}}
             ;{:form-params {:command remote}}
             ;{:edn-params {:command remote}}
             ))

      ;(chsk-send! [:event-manager/query [[:competition/name :competition/location]]])
      )))

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
     :send    (transit-post "http://localhost:1337/commands")                                              ;(sente-post)
     }))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))