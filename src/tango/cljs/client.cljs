(ns tango.cljs.client
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [goog.dom :as gdom]
            [om.next :as om :refer-macros [defui]]
            [om.dom :as dom]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [taoensso.sente :as sente :refer (cb-success?)]
            [datascript.core :as d]))

(def test-data
  {:competition/name "TurboMegatävling",
   :competition/date #inst "2014-11-22T00:00:00.000-00:00",
   :competition/location "THUNDERDOME",
   :competition/options
   {:dance-competition/same-heat-all-dances true,
    :presentation/chinese-fonts true,
    :dance-competition/heat-text-on-adjudicator-sheet true,
    :dance-competition/name-on-number-sign true,
    :dance-competition/skip-adjudicator-letter true,
    :presentation/courier-font "NSimSun",
    :dance-competition/adjudicator-order-final true,
    :dance-competition/random-order-in-heats true,
    :dance-competition/club-on-number-sign true,
    :dance-competition/adjudicator-order-other true,
    :presentation/arial-font "SimSun",
    :printer/preview true,
    :printer/printer-select-paper true}
   :competition/panels
   [{:adjudicator-panel/name "1",
     :adjudicator-panel/id 4,
     :adjudicator-panel/adjudicators
     [{:adjudicator/id 1,
       :adjudicator/name "Anders",
       :adjudicator/country "Sweden"}
      {:adjudicator/id 2,
       :adjudicator/name "Bertil",
       :adjudicator/country ""}]}
    {:adjudicator-panel/name "2",
     :adjudicator-panel/id 5,
     :adjudicator-panel/adjudicators
     [{:adjudicator/id 2,
       :adjudicator/name "Bertil",
       :adjudicator/country ""}
      {:adjudicator/id 3,
       :adjudicator/name "Cesar",
       :adjudicator/country ""}]}],
   :competition/adjudicators
   [{:adjudicator/id 1,
     :adjudicator/name "Anders",
     :adjudicator/country "Sweden"}
    {:adjudicator/id 2,
     :adjudicator/name "Bertil",
     :adjudicator/country ""}
    {:adjudicator/id 3,
     :adjudicator/name "Cesar",
     :adjudicator/country ""}],
   :competition/activities
   [{:activity/name "",
     :activity/number -1,
     :activity/comment "A comment",
     :activity/id 35,
     :activity/position 1,
     ;:activity/time nil,
     ;:activity/source nil
     }
    {:activity/name "Hiphop Singel Star B",
     :activity/number 1,
     :activity/comment "",
     :activity/id 37,
     :activity/position 2,
     :activity/time #inst "2014-11-22T10:00:00.000-00:00",
     :activity/source
     {:round/status :not-started,
      :round/panel
      {:adjudicator-panel/name "2",
       :adjudicator-panel/id 5,
       :adjudicator-panel/adjudicators
       [{:adjudicator/id 2,
         :adjudicator/name "Bertil",
         :adjudicator/country ""}
        {:adjudicator/id 3,
         :adjudicator/name "Cesar",
         :adjudicator/country ""}]},
      :round/class-id 41,
      :round/id 36,
      :round/dances [{:dance/name "Medium"}],
    :round/results
    [{:result/participant-number 30,
      :result/recalled "",
      :result/judgings
      ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
       {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
       {:judging/adjudicator 3, :juding/marks [{:mark/x true}]})}
     {:result/participant-number 31,
      :result/recalled "",
      :result/judgings
      ({:judging/adjudicator 1, :juding/marks [{:mark/x false}]}
       {:judging/adjudicator 2, :juding/marks [{:mark/x true}]}
       {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}
     {:result/participant-number 32,
      :result/recalled "",
      :result/judgings
      ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
       {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
       {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}],
    :round/index 0,
    :round/starting
    [{:participant/name "Rulle Trulle",
      :participant/club "Sinus",
      :participant/number 30,
      :participant/id 38}
     {:participant/name "Hush Bush",
      :participant/club "Zilson",
      :participant/number 31,
      :participant/id 39}
     {:participant/name "Banana Hamock",
      :participant/club "Zzzz",
      :participant/number 32,
      :participant/id 40}],
    :round/heats 2,
    :round/type :normal-x,
    :round/number 1,
    :round/start-time #inst "2014-11-22T10:00:00.000-00:00",
    :round/recall 6}}],
 :competition/classes
 ({:class/adjudicator-panel
   {:adjudicator-panel/name "1",
    :adjudicator-panel/id 4,
    :adjudicator-panel/adjudicators
    [{:adjudicator/id 1,
      :adjudicator/name "Anders",
      :adjudicator/country "Sweden"}
     {:adjudicator/id 2,
      :adjudicator/name "Bertil",
      :adjudicator/country ""}]},
   :class/starting
   [{:participant/name "Rulle Trulle",
     :participant/club "Sinus",
     :participant/number 30,
     :participant/id 38}
    {:participant/name "Hush Bush",
     :participant/club "Zilson",
     :participant/number 31,
     :participant/id 39}
    {:participant/name "Banana Hamock",
     :participant/club "Zzzz",
     :participant/number 32,
     :participant/id 40}],
   :class/dances
   [{:dance/name "Medium"}
    {:dance/name "Tango"}
    {:dance/name "VienWaltz"}],
   :class/remaining
   [{:participant/name "Rulle Trulle",
     :participant/club "Sinus",
     :participant/number 30,
     :participant/id 38}
    {:participant/name "Hush Bush",
     :participant/club "Zilson",
     :participant/number 31,
     :participant/id 39}
    {:participant/name "Banana Hamock",
     :participant/club "Zzzz",
     :participant/number 32,
     :participant/id 40}],
   :class/position 1,
   :class/name "Hiphop Singel Star B",
   :class/rounds
   [{:round/status :not-started,
     :round/panel
     {:adjudicator-panel/name "2",
      :adjudicator-panel/id 5,
      :adjudicator-panel/adjudicators
      [{:adjudicator/id 2,
        :adjudicator/name "Bertil",
        :adjudicator/country ""}
       {:adjudicator/id 3,
        :adjudicator/name "Cesar",
        :adjudicator/country ""}]},
     :round/class-id 41,
     :round/id 36,
     :round/dances [{:dance/name "Medium"}],
     :round/results
     [{:result/participant-number 30,
       :result/recalled "",
       :result/judgings
       ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
        {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
        {:judging/adjudicator 3, :juding/marks [{:mark/x true}]})}
      {:result/participant-number 31,
       :result/recalled "",
       :result/judgings
       ({:judging/adjudicator 1, :juding/marks [{:mark/x false}]}
        {:judging/adjudicator 2, :juding/marks [{:mark/x true}]}
        {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}
      {:result/participant-number 32,
       :result/recalled "",
       :result/judgings
       ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
        {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
        {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}],
     :round/index 0,
     :round/starting
     [{:participant/name "Rulle Trulle",
       :participant/club "Sinus",
       :participant/number 30,
       :participant/id 38}
      {:participant/name "Hush Bush",
       :participant/club "Zilson",
       :participant/number 31,
       :participant/id 39}
      {:participant/name "Banana Hamock",
       :participant/club "Zzzz",
       :participant/number 32,
       :participant/id 40}],
     :round/heats 2,
     :round/type :normal-x,
     :round/number 1,
     :round/start-time #inst "2014-11-22T10:00:00.000-00:00",
     :round/recall 6}],
   :class/id 41}
  {:class/adjudicator-panel nil,
   :class/starting
   [{:participant/name "Ringo Stingo",
     :participant/club "Kapangg",
     :participant/number 20,
     :participant/id 42}
    {:participant/name "Greve Turbo",
     :participant/club "OOoost",
     :participant/number 21,
     :participant/id 43}],
   :class/dances [],
   :class/remaining
   [{:participant/name "Ringo Stingo",
     :participant/club "Kapangg",
     :participant/number 20,
     :participant/id 42}
    {:participant/name "Greve Turbo",
     :participant/club "OOoost",
     :participant/number 21,
     :participant/id 43}],
   :class/position 2,
   :class/name "Hiphop Singel Star J Fl",
   :class/rounds [],
   :class/id 44})})

;https://github.com/omcljs/om/wiki/Quick-Start-%28om.next%29

(enable-console-print!)

;; (def output-el (.getElementById js/document "output"))

;; (defn ->output! [fmt & args]
;;   (let [msg (apply encore/format fmt args)]
;;     (timbre/debug msg)
;;     (aset output-el "value" (str "• " (.-value output-el) "\n" msg))
;;     (aset output-el "scrollTop" (.-scrollHeight output-el))))

(defn log [m]
  (.log js/console m))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Init DB


(def conn (d/create-conn {:competition/classes {:db/cardinality :db.cardinality/many
                                                :db/valueType :db.type/ref}
                          :competition/options {:db/isComponent true
                                                :db/valueType :db.type/ref}

                          :competition/panels {:db/cardinality :db.cardinality/many
                                                :db/valueType :db.type/ref}

                          :adjudicator-panel/adjudicators {:db/cardinality :db.cardinality/many
                                                           :db/valueType :db.type/ref}

                          :competition/activities {:db/cardinality :db.cardinality/many
                                                   :db/valueType :db.type/ref}

                          :activity/source {:db/cardinality :db.cardinality/one
                                            :db/valueType :db.type/ref}

                          :round/dances  {:db/cardinality :db.cardinality/many
                                                   :db/valueType :db.type/ref}

                          ;:class/name {:db/unique :db.unique/identity}
                          }))

;; Test of transacting 'flat'
;; (d/transact! conn [{:db/id 1 :competition/name "A"}
;;                    {:db/id 1 :competition/classes 2}
;;                    {:db/id 1 :competition/classes 3}
;;                    {:db/id 2 :class/name "Disco"}
;;                    {:db/id 3 :class/name "Tango"}])

;; Test of transacting list of e/id
;; (d/transact! conn [{:db/id 1 :competition/name "A"}
;;                    {:db/id 1 :competition/classes [2 3]}
;;                    {:db/id 2 :class/name "Disco"}
;;                    {:db/id 3 :class/name "Tango"}])

;; Test of transaction with nested creation
;; (d/transact! conn [{:db/id -1 :competition/name "A"}
;;                    {:db/id -1 :competition/classes
;;                     [{:db/id -2 :class/name "Disco"}
;;                      {:db/id -3 :class/name "Tango"}]}

;;                    {:db/id -1 :competition/location "Vås"}

;;                    {:db/id -1 :competition/options
;;                     {:option/is-good true}}

;;                    {:db/id -4 :competition/name "B" :competition/location "Ups"}
;;                    ])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Test with Small Example

(declare test-data)

(log (select-keys test-data [:competition/name]))
;(d/transact! conn [(select-keys test-data [:competition/name :competition/options])])

;; TODO - competition/adjudicators och panels, must fix ID ref, and round panel
;; TODO - do all nil values need to be filtered?
(def tx-data
  (let [tx (select-keys test-data [:competition/name :competition/activities])]
    (:activity/source (second (:competition/activities tx)))))

(log tx-data)

(d/transact! conn [tx-data])

;[(pull ?e ?selector) ...]
;; (log
;;  (d/q '[:find [(pull ?c [:class/name]) ...] 
;;         :where
;;         [?e :competition/name "A"]
;;         [?e :competition/classes ?c]]
;;       (d/db conn)))

 (log (pr-str (d/db conn)))
;; (d/transact! conn
;;   [{:db/id -1
;;     :app/title "Hello, DataScript!"
;;     :app/count 0}])

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
  (do
    (log (apply str (map :competition/name d)))
    ;(d/transact! (d/db conn) )
    (d/transact! conn [{:db/id 1 :competition/name (apply str (map :competition/name d))}])
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
    (log (str "Push event from server: " ev-msg))
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
   :action (fn [] (chsk-send! [:event-manager/query [[:competition/name :competition/location]]]))})

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
       (apply dom/tbody nil (map competition competitions))
      
       )
      (dom/button
       #js {:onClick
            (fn [e]
              (test-query-click this)
                                        ;(chsk-send! [:event-manager/query [[:competition/name :competition/location]]])
               )}
        "Query")
      )
    
     )))



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

(def reconciler
  (om/reconciler
    {:state conn
     :remotes [:remote]
     :parser (om/parser {:read read :mutate mutate})
     :send (sente-post) ;(fn [x y] (log (str "Sending to server: x" x " y: " y)))
     }))

;; (om/add-root! reconciler
;;   Tester (gdom/getElement "app"))

(om/add-root! reconciler
  CompetitionsView (gdom/getElement "app"))

;; (defui Tester
;;   static om/IQuery
;;   (query [this]
;;          [{:app/competitions [:competition/name :competition/location {:competition/classes [:class/name]}]}])
;;   Object
;;   (render
;;    [this]
;;    (let [{:keys [app/competitions]} (get-in (om/props this) [:app/competitions 0])
;;          entity (first (:app/competitions (om/props this)))]
;;      ;;(log "Props =>")
;;      ;;(log (om/props this))
;;      (dom/div
;;       nil
;;       (dom/div
;;        nil
;;        (str "Competition 1 " (:competition/name entity)))
;;       (dom/div
;;        nil
;;        (str "Location " (:competition/location entity)))
;;       (dom/div
;;        nil
;;        (str "# classes " (count (:competition/classes entity))))
;;       (competition)
;;       (dom/div
;;        nil
;;        (dom/button
;;         #js {:onClick
;;              (fn [e]
;;                (test-query-click this)
;;                ;(chsk-send! [:event-manager/query [[:competition/name :competition/location]]])
;;                )}
;;         "Query"))))))


