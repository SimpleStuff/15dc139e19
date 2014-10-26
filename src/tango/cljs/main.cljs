(ns tango-client.core
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)]
   ;;[cljs.core.match.macros :refer (match)]
   )
  (:require [om.core :as om :include-macros true]
            [om.dom :as dom :include-macros true]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            ;[cljs.core.match]
            [taoensso.sente :as sente :refer (cb-success?)]
            [clojure.string]))

;; Sente: https://github.com/ptaoussanis/sente
;;; Utils
(enable-console-print!)

(defn log [m]
  (.log js/console m))

(log "ClojureScript appears to have loaded correctly.")

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-click [e owner]
  (log "Clicked w/o callback")
  (chsk-send! [:test.event/clicked {:data "no callback"}]))

(defn on-edit [e owner]
  (log "Edit w/o callback")
  (chsk-send! [:test.event/clicked {:data (.. e -target -value)}]))

(defn on-click-cb [e owner]
  (log "Click w callback")
  (chsk-send! [:test.event/clicked {:data "Callback"}] 5000
              (fn [cb-reply] (log (str "Callback reply: " cb-reply)))))

(defn widget [data owner]
  (reify
    om/IRender
    (render [this]
      (dom/p
       nil
       (dom/div
        nil
        (dom/h2 nil (:text data))
        (dom/button #js {:onClick #(on-click % owner)} "w/o callback"))
       (dom/div
        nil
        (dom/label nil "Sending data :")
        (dom/input #js {:type "text" :onChange #(on-edit % owner)}))
       (dom/div
        nil
        (dom/button #js {:onClick #(on-click-cb % owner)} "callback"))))))

(om/root widget {:text "Example om/sente page"}
  {:target (. js/document (getElementById "main"))})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket handling
(defn- event-handler [[id data :as ev] _]
  (log (str "Event: " ev))
  ;; (match [id data]
  ;;   ;; TODO Match your events here <...>
  ;;   [:chsk/state [:first-open _]] (log "Channel socket successfully established!")
  ;;   [:chsk/state new-state] (log (str "Chsk state change: " new-state))
  ;;   [:chsk/recv payload] (log (str "Push event from server: " payload))
  ;;   :else (log (str "Unmatched event: " ev)))
  )

(defonce chsk-router
  (sente/start-chsk-router-loop! event-handler ch-chsk))
