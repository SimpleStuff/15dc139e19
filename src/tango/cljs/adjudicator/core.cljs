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

(defui MainComponent
  static om/IQuery
  (query [_]
    [:app/status])
  Object
  (render
    [this]
    (let [app (om/props this)
          status (:app/status app)
          next-status (if (not= status :on) :on :off)]
      ;(log status)
      (dom/div nil
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
                             (log "Command"))} "Command"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Remote Posts

;http://jeremyraines.com/2015/11/16/getting-started-with-clojure-web-development.html
(defn transit-post [url]
  (fn [edn cb]
    (log edn)
    (.send XhrIo url
           (fn [e]
             (log e)
             ;(this-as this
             ;  (log (t/read (t/reader :json)
             ;               (.getResponseText this)))
             ;  (cb (t/read (t/reader :json) (.getResponseText this))))
             )
           "POST" (t/write (t/writer :json) edn)
           #js {"Content-Type" "application/transit+json"})))

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
     :remotes [:remote]
     :parser  (om/parser {:read r/read :mutate m/mutate})
     :send    (transit-post "http://localhost:1337/commands")                                              ;(sente-post)
     }))

(om/add-root! reconciler
              MainComponent (gdom/getElement "app"))