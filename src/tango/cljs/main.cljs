(ns tango-client
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs.core.match :refer-macros [match]]
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
;; (defn on-click [e owner]
;;   (log "Clicked w/o callback")
;;   (chsk-send! [:test.event/clicked {:data "no callback"}]))

;; (defn on-edit [e owner]
;;   (log "Edit w/o callback")
;;   (chsk-send! [:test.event/clicked {:data (.. e -target -value)}]))

;; (defn on-click-cb [e owner]
;;   (log "Click w callback")
;;   (chsk-send! [:test.event/clicked {:data "Callback"}] 5000
;;               (fn [cb-reply] (log (str "Callback reply: " cb-reply)))))

(defn on-click-get-dancers []
  (log "Clicked w/o callback")
  (chsk-send! [:dancers/get {:all-of-them-now-damit! :please}]))

(defn on-file-read [e file-reader]
  (let [result (.-result file-reader)]
    (chsk-send! [:file/import {:content result}])))

(defn on-click-import-file [e]
  (log "Import clicked")
  (let [file (.item (.. e -target -files) 0)
        r (js/FileReader.)]
    (set! (.-onload r) #(on-file-read % r))
    (.readAsText r file)))

(def app-state 
  (atom 
   {:dancers [{:dancer/name "Bobo"} {:dancer/name "Rolf"}]}))

;; (defn dancers-component []
;;   [:div
;;    [:h2 "Dancers :"]
;;    [:ul
;;     (for [template (:dancers @app-state)]
;;       ^{:key template} [:li "" (:dancer/name template)])]
;;    [:input.btn.btn-default {:type "button" :value "Get Dancers"
;;                             :on-click on-click-get-dancers}]])

(defn import-component []
  [:div
   [:input.btn.btn-default {:type "file" :value "Import file"
                            :onChange #(on-click-import-file %)}]
   ;; [:input.btn.btn-default {:type "button" :value "Ping"
   ;;                          :on-click #(chsk-send! [:client/ping {:content 1}])}]
   [:div
    [:h2 "Competition"]
    [:h3 (str "Name :" (:competition/name @app-state)) ]]
   ])

(defn ^:export run []
  (reagent/render-component [import-component] (.-body js/document)))



;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket handling
(defn- event-handler [[id data :as ev] _]
  (log (str "Event: " ev))
  (match [id data]
    ;; TODO Match your events here <...>
         [:chsk/recv [:file/imported content]]
         (do (swap! app-state #(:content content)) (log (:content content)))
    [:chsk/state [:first-open _]] (log "Channel socket successfully established!")
    ;[:chsk/state new-state] (log (str "Chsk state change: " new-state))
    ;[:chsk/recv payload] (log (str "Push event from server: " payload))
    :else (log (str "Unmatched event: " ev)))
  )

(defonce chsk-router
  (sente/start-chsk-router-loop! event-handler ch-chsk))
