(ns tango.cljs.client
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [datascript.core :as d]
            [tango.ui-db :as uidb]))


;https://github.com/omcljs/om/wiki/Quick-Start-%28om.next%29

(enable-console-print!)

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB
(defonce conn (d/create-conn uidb/schema))

;;  (d/q '[:find [(pull ?c [:class/name]) ...] 
;;         :where
;;         [?e :competition/name "A"]
;;         [?e :competition/classes ?c]]
;;       (d/db conn)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Sente Socket setup

(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" ; Note the same path as before
       {:type :auto ; e/o #{:auto :ajax :ws}
       })]
  (def chsk       chsk)
  (def ch-chsk    ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def chsk-state state)   ; Watchable, read-only atom
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Server message handling

(defn handle-query-result [d]
  (let [clean-data (uidb/sanitize d)]
     ;(log clean-data)   
    ;(log (apply str (map :competition/name clean-data)))
                                        ;(d/transact! (d/db conn) )
    (d/transact! conn [clean-data])
    (log (d/q '[:find [(pull ?c [:class/name]) ...]
                :where
                [?e :competition/name "Rikstävling disco"]
                [?e :competition/classes ?c]]
              (d/db conn)))
    ;(d/transact! conn [{:db/id 1 :competition/name (apply str (map :competition/name clean-data))}])
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Sente message handling

(defmulti event-msg-handler :id) ; Dispatch on event-id
;; Wrap for logging, catching, etc.:
(defn event-msg-handler* [{:keys [id ?data event] :as ev-msg}]
  (event-msg-handler {:id (first ev-msg)
                      :?data (second ev-msg)}))

(defmethod event-msg-handler :default ; Fallback
  [{:keys [event] :as ev-msg}]
  (log (str "Unhandled event: " ev-msg)))

(defmethod event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (if (= ?data {:first-open? true})
    (log "Channel socket successfully established!")
    (log (str "Channel socket state change: " ?data))))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (let [[topic payload] ?data]
    ;(log (str "Push event from server: " ev-msg))
    (log (str "Push event from server: " topic))
    (when (= topic :event-manager/query-result)
      (handle-query-result (second ?data)))))

(defmethod event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (log (str "Handshake: " ?data))))

(defmethod event-msg-handler :event-manager/query-result
  [{:as ev-msg :keys [?data]}]
  (log (str "Event Manager Query Result " ?data)))

(defonce chsk-router
  (sente/start-chsk-router-loop! event-msg-handler* ch-chsk))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Read

(defmulti read om/dispatch)

;; (defmethod read :app/counter
;;   [{:keys [state query]} _ _]
;;   {:value (d/q '[:find [(pull ?e ?selector) ...]
;;                  :in $ ?selector
;;                  :where [?e :app/title]]
;;             (d/db state) query)})

;; The signature of a read function is [env key params]. env is a hash map containing any context 
;; necessary to accomplish reads. key is the key that is being requested to be read. 
;; Finally params is a hash map of parameters that can be used to customize the read. 
;; In many cases params will be empty.
(defmethod read :app/competitions
  [{:keys [state query] :as env} key params]
  {:value (do
            ;(log (str "Env: " env " --- Key : " key " --- Params" params))
            (log "Read app/competitions")
            (d/q '[:find [(pull ?e ?selector) ...]
                   :in $ ?selector
                   :where [?e :competition/name]]
                 (d/db state) query)) ;(log (str "Read Comp, state " state " , query" query))
   :remote true})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Mutate

(defmulti mutate om/dispatch)

(defmethod mutate 'app/increment
  [{:keys [state]} _ entity]
  {:value {:keys [:app/counter]}
   :action (fn [] (d/transact! state
                    [(update-in entity [:app/count] inc)]))})

;; Mutations should return a map for :value. This map can contain two keys - 
;; :keys and/or :tempids. The :keys vector is a convenience that communicates what 
;; read operations should follow a mutation. :tempids will be discussed later. 
;; Mutations can easily change multiple aspects of the application (think Facebook "Add Friend"). 
;; Adding :value with a :keys vector helps users identify stale keys which should be re-read.
;; (defmethod mutate 'app/name
;;   [{:keys [state]} _ _]
;;   {:value {:keys [:competition/name]}
;;    :action (fn [] (d/transact! state [{:db/id 1 :competition/name "B"}]))})

(defmethod mutate 'app/name
  [{:keys [state]} _ _]
  {:value {:keys [:app/competitions]}
   :action ;(fn [] (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))
   (fn [] (chsk-send! [:event-manager/query ['[*] [:competition/name "Rikstävling disco"]]]))
   })

(defn test-query-click [t]
  (do
    (log "Test Click")
    (om/transact! t '[(app/name)])))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

(defui Competition
  Object
  (render
   [this]
   (let [competition (om/props this)]
     (dom/tr
      nil
      (dom/td nil (:competition/name competition))
      (dom/td nil (:competition/location competition))))))

(def competition (om/factory Competition))

(defui CompetitionsView
  static om/IQuery
  (query [this]
         [{:app/competitions
           [:competition/name :competition/location]}])
  Object
  (render
   [this]
   (let [competitions (:app/competitions (om/props this))]
     (dom/div
      nil
      (dom/h2
       nil
       "Mina tävlingar")
      (dom/table
       nil
       (dom/thead
        nil
        (dom/tr
         nil
         (dom/th nil "Namn")
         (dom/th nil "Plats")))
       (apply dom/tbody nil (map competition competitions)))
      (dom/button
       #js {:onClick
            (fn [e]
              (test-query-click this))}
       "Query")))))

(defui MenuComponent
  Object
  (render
   [this]
   (dom/div nil (dom/button #js {:onClick #(log "T'vlingar")} "Tävlingar")
            (dom/div nil ((om/factory CompetitionsView))))))


(defn transit-post [url]
  (fn [{:keys [remote]} cb]
    (do
      (log "Post")
      (log (str "Remote " remote))
      (cb {}))
    ;; (.send XhrIo url
    ;;   (fn [e]
    ;;     (this-as this
    ;;       (cb (transit/read (om/reader) (.getResponseText this)))))
    ;;   "POST" (transit/write (om/writer) remote)
    ;;   #js {"Content-Type" "application/transit+json"})
    ))

(defn sente-post []
  (fn [{:keys [remote] :as env} cb]
    (do
      (log "Env > ")
      (log env)
      (log (str "Sent to Tango Backend => " remote))
      (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))))

;; TODO - since conn is set the read query will be run twice at start up - delay read or something.. 
(def reconciler
  (om/reconciler
    {:state conn
     :remotes [:remote]
     :parser (om/parser {:read read :mutate mutate})
     :send (sente-post) ;(fn [x y] (log (str "Sending to server: x" x " y: " y)))
     }))

;; (om/add-root! reconciler
;;   Tester (gdom/getElement "app"))

;; (om/add-root! reconciler
;;   CompetitionsView (gdom/getElement "app"))

(om/add-root! reconciler
  MenuComponent (gdom/getElement "app"))


