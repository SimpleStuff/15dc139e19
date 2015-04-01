(ns tango-client
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [reagent.core :as reagent :refer [atom]]
            [cljs.core.async :as async :refer (<! >! put! chan)]
            [cljs.core.match :refer-macros [match]]
            [taoensso.sente :as sente :refer (cb-success?)]
            [clojure.string]))

;; Sente: https://github.com/ptaoussanis/sente
;http://markusslima.github.io/bootstrap-filestyle/
;http://getbootstrap.com/components/
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

(defn on-export-click [e competition]
  (log "Export clicked")
  (log (str competition))
  (chsk-send! [:file/export {:file/format :dance-perfect
                             :file/content competition}]))

;; (defonce app-state
;;   (atom {:competitions []}))

(defonce app-state 
  (atom {:competitions
         [{:competition/date #inst "2014-11-22T00:00:00.000-00:00",
           :competition/name "TurboMegatävling",
           :dance-perfect/version "4.1",
           :competition/location "THUNDERDOME",
           :competition/classes
           [{:class/name "Hiphop Singel Star B", :class/competitors
             [{:competitor/name "Rulle Trulle", :competitor/number 1, :competitor/club "Rulles M&M"}
              {:competitor/name "Katchyk Wrong", :competitor/number 2, :competitor/club "Sccchhh"}]}
            {:class/name "Hiphop Singel Star J Fl", :class/competitors
             [{:competitor/name "Ringo Stingo" :competitor/number 20, :competitor/club "Kapangg"}
              {:competitor/name "Greve Turbo", :competitor/number 21, :competitor/club "OOoost"}]}]}]}))

;; TODO - Let the server return dancers in a UI-normalized way
(defn get-dancers [competitions]
  (into #{}
        (map #(dissoc % :competitor/number) 
             (for [competition competitions
                   classes (:competition/classes competition)
                   competitors (:class/competitors classes)]
               competitors))))

(defonce component-state
  (atom {:competitions-visible false}))

(defn competitor-component [competitors include-number?]
  [:div ""
   [:ul
    (for [competitor competitors]
      ^{:key competitor}
      [:li 
       (str (if include-number?
              (str "Tävlande nummer : " (:competitor/number competitor) " - "))
            (str (:competitor/name competitor)
                 " från "
                 (:competitor/club competitor)))])]])

(defn class-component [classes]
  [:h3 "Classes"]
  [:div
   (for [cls classes]
     ^{:key cls}
     [:div
      (:class/name cls)
      [competitor-component (:class/competitors cls) true]])])



(defn competition-item []
  (let [open (atom false)]
    (fn [competition]
      [:li 
       [:div.view
        [:label {:on-click #(log "Klickz")} (str (:competition/name competition) " i "
                      (:competition/location competition) " den "
                      (:competition/date competition))
         ]
        [:input.btn.btn-default
         {:type "button"
          :value (if @open "Stäng" "Öppna")
          :on-click #(swap! open not)}]]
       (if @open
         [:div
          [:input.btn.btn-default
           {:type "button"
            :value "Exportera"
            ;; TODO - send competition id when we got back-end storage
            :on-click #(on-export-click % competition)}]
          [:a {:id "export-download-link"
               :href (str "/exported-files/" (:competition/name competition) ".xml")
               :download (str (:competition/name competition) ".xml")}]
          [:h4 "Klasser :"]
          [class-component (:competition/classes competition)]])])))

(defn competitors-component []
  [:div
   [:h2 "Tillgängliga dansare"]
   [competitor-component (get-dancers (:competitions @app-state))]])

(defn import-component []
  [:div
   
   [:h2 "Tillgängliga tävlingar"]
   [:ul
    (for [competition (:competitions @app-state)]
      ^{:key competition} [competition-item competition])]
   [:h2 "Importera en ny tävling : "]
   [:input.btn.btn-default {:type "file" :value "Import file"
                            :onChange #(on-click-import-file %)}]])

(defn menu-component []
  (let [visible-component (atom :none)]
    (fn []
      [:div.container
       [:div.header
        [:h2 "Välkommen till Tango!"]
        [:input.btn.btn-primary.btn-lg.navbar-btn
         {:type "button" :value "Tävlingar" :on-click #(reset! visible-component :competitions)}]
        [:input.btn.btn-primary.btn-lg
         {:type "button" :value "Dansare" :on-click #(reset! visible-component :competitors)}]
        [:input.btn.btn-primary.btn-lg
         {:type "button" :value "Domare" :on-click #(reset! visible-component :adjudicators)}]
        ;[:h2 (str "visible-component " @visible-component)]
        ]
       (condp = @visible-component
         :competitions [import-component]
         :competitors [competitors-component]
         :adjudicators [:div]
         :none [:div])
       ])))

(defn ^:export run []
  (reagent/render-component [menu-component] (.-body js/document)))

;; Exempel till "Export"
;; <a id="document-download-link" download="project.goy" href=""></a>

;; (defn save-document []
;; (let [download-link (. js/document (getElementById "document-download-link"))
;; app-state-to-save (get-in @app/app-state [:main-app])
;; document-content (pr-str app-state-to-save)
;; compressed-content (.compressToBase64 js/LZString document-content)
;; href-content (str "data:application/octet-stream;base64," compressed-content)]
;; (set! (.-href download-link) href-content)
;; (.click download-link)))

(defn handle-export []
  (let [export-link (. js/document (getElementById "export-download-link"))]
    (log "Exporting competition")
    (.click export-link)
    ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket handling
(defn- event-handler [[id data :as ev] _]
  (log (str "Event: " ev))
  (match [id data]
    ;; TODO Match your events here <...>
         [:chsk/recv [:file/imported content]]
         (do
           (swap! app-state #(hash-map :competitions (conj (:competitions %) (:content content))))
           (log (str @app-state))
           )
         [:chsk/recv [:file/export content]]
         (handle-export)
    [:chsk/state [:first-open _]] (log "Channel socket successfully established!")
    ;[:chsk/state new-state] (log (str "Chsk state change: " new-state))
    ;[:chsk/recv payload] (log (str "Push event from server: " payload))
    :else (log (str "Unmatched event: " ev)))
  )

(defonce chsk-router
  (sente/start-chsk-router-loop! event-handler ch-chsk))
