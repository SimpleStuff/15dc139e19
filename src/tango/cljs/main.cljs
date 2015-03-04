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

;; (def app-state 
;;   (atom 
;;    {:competition/date #inst "2014-11-22T00:00:00.000-00:00",
;;     :competition/name "TurboMegatävling",
;;     :dance-perfect/version "4.1",
;;     :competition/location "THUNDERDOME",
;;     :competition/classes
;;     [{:class/name "Hiphop Singel Star B", :class/competitors
;;       [{:competitor/name "Rulle Trulle", :competitor/number 1, :competitor/club "Rulles M&M"}
;;        {:competitor/name "Katchyk Wrong", :competitor/number 2, :competitor/club "Sccchhh"}]}
;;      {:class/name "Hiphop Singel Star J Fl", :class/competitors
;;       [{:competitor/name "Ringo Stingo" :competitor/number 20, :competitor/club "Kapangg"}
;;        {:competitor/name "Greve Turbo", :competitor/number 21, :competitor/club "OOoost"}]}]}))

(def app-state (atom []))

(defn competitor-component [competitors]
  [:div ""
   [:ul
    (for [competitor competitors]
      ^{:key competitor}
      [:li
       (str "Competitor number: "
            (:competitor/number competitor)
            " - "
            (:competitor/name competitor)
            " / "
            (:competitor/club competitor))])]])

(defn class-component [classes]
  [:h3 "Classes"]
  [:div
   (for [cls classes]
     ^{:key cls} [:div
                  (:class/name cls)
                  [competitor-component (:class/competitors cls)]
                  ])])

(defn competition-component []
  [:div
   [:h2 "Competition"]
   [:h3 (str "Name :" (:competition/name @app-state)) ]
   [:h3 (str "Date :" (:competition/date @app-state)) ]
   [:h3 (str "Location :" (:competition/location @app-state))]
   [:h3 (str "Classes :")] 
   [class-component (:competition/classes @app-state)]])

(defn import-component []
  [:div
   [:input.btn.btn-default {:type "file" :value "Import file"
                            :onChange #(on-click-import-file %)}]
   ;; [:input.btn.btn-default {:type "button" :value "Ping"
   ;;                          :on-click #(chsk-send! [:client/ping {:content 1}])}]
   ;; [:div
   ;;  [:h2 "Competition"]
   ;;  [:h3 (str "Name :" (:competition/name @app-state)) ]]
   [competition-component]
   ])

(defn menu-component []
  [:div
   [:input.btn.btn-default {:value "Tävlingar"}]])

(defn ^:export run []
  (reagent/render-component [menu-component] (.-body js/document)))

;; {:competition/date #inst "2014-11-22T00:00:00.000-00:00", :competition/name "TurboMegatävling", :dance-perfect/version "4.1", :competition/location "THUNDERDOME", :competition/classes [{:class/name "Hiphop Singel Star B", :class/competitors [{:competitor/name "Rulle Trulle", :competitor/number 1, :competitor/club "Rulles M&M"} {:competitor/name "Katchyk Wrong", :competitor/number 2, :competitor/club "Sccchhh"}]} {:class/name "Hiphop Singel Star J Fl", :class/competitors [{:competitor/name "Ringo Stingo", :competitor/number 20, :competitor/club "Kapangg"} {:competitor/name "Greve Turbo", :competitor/number 21, :competitor/club "OOoost"}]}]}}

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
