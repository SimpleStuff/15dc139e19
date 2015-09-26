(ns tango.client
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
;; (defonce app-state
;;   (atom {:competitions []}))

(defonce app-state
  (atom {:selected-page :import

         ;; :competition
         ;; {:competition/date #inst "2014-11-22T00:00:00.000-00:00",
         ;;  :competition/name "Elittävling",
         ;;  :dance-perfect/version "4.1",
         ;;  :competition/location "VÄSTERÅS",
         ;;  :competition/classes
         ;;  [{:class/name "Hiphop Singel Star B",
         ;;    :class/adjudicator-panel 1
         ;;    :class/dances
         ;;    [{:dance/name "Medium"} {:dance/name "Waltz"}]
         ;;    :class/competitors
         ;;    [{:competitor/name "Saga Boström-Fors", :competitor/number 10, :competitor/club "M&M"}
         ;;       {:competitor/name "Tyra Hedin", :competitor/number 11, :competitor/club "Uddans"}
         ;;       {:competitor/name "Elina Ahlberg", :competitor/number 12, :competitor/club "SDC"}
         ;;       {:competitor/name "Thyra Söderström", :competitor/number 13, :competitor/club "Uddans"}
         ;;       {:competitor/name "Wilma Lindström Åslund", :competitor/number 14, :competitor/club "MD"}]}
           
         ;;   {:class/name "Hiphop Singel Star J Fl",
         ;;    :class/adjudicator-panel 0
         ;;    :class/dances [{:dance/name "Quick"}]
         ;;    :class/competitors
         ;;    [{:competitor/name "Tilda Strandberg", :competitor/number 30, :competitor/club "Uddans"}
         ;;     {:competitor/name "Tove Gärdin", :competitor/number 31, :competitor/club "BF"}
         ;;     {:competitor/name "Esther Wallmo", :competitor/number 32, :competitor/club "Uddans"}
         ;;     {:competitor/name "Felicia Dackell", :competitor/number 33, :competitor/club "Uddans"}
         ;;     {:competitor/name "Emma Fredriksson", :competitor/number 34, :competitor/club "DVT"}]}
           
         ;;   {:class/name "Hiphop Singel Star J Po",
         ;;    :class/adjudicator-panel 2
         ;;    :class/dances []
         ;;    :class/competitors
         ;;    [{:competitor/name "Axel Carlsson", :competitor/number 60, :competitor/club "DTLH/DV"}
         ;;     {:competitor/name "Tom Matei", :competitor/number 61, :competitor/club "SDC"}
         ;;     {:competitor/name "Jacob Olsson", :competitor/number 62, :competitor/club "DTLH/DV"}]}]}

         :competition
         {:competition/name "TurboMegatävling"
          :competition/date #inst "2014-11-22T00:00:00.000-00:00"
          :competition/location "THUNDERDOME"
          :competition/classes
          [{:class/name "Hiphop Singel Star B"
            :class/position 1
            :class/adjudicator-panel 1
            :class/dances
            [{:dance/name "Medium"}
             {:dance/name "Tango"}
             {:dance/name "VienWaltz"}
             {:dance/name "Foxtrot"}
             {:dance/name "Quickstep"}
             {:dance/name "Samba"}
             {:dance/name "Cha-Cha"}
             {:dance/name "Rumba"}
             {:dance/name "Paso-Doble"}
             {:dance/name "Jive"}]
            :class/competitors
            [{:competitor/name "Rulle Trulle"
              :competitor/club "Sinclairs"
              :competitor/number 30}
             {:competitor/name "Milan Lund"
              :competitor/club "Wilson"
              :competitor/number 31}
             {:competitor/name "Douglas Junger"
              :competitor/club "RGDT"
              :competitor/number 32}]
            :class/results
            [{:result/round "S"
              :result/adjudicators
              [{:adjudicator/number 3 :adjudicator/position 0}
               {:adjudicator/number 4 :adjudicator/position 1}
               {:adjudicator/number 5 :adjudicator/position 2}]
              :result/dance {:dance/name "X-Quick Forward"}
              :result/results
              [{:competitor/number 30
                :competitor/recalled ""
                :competitor/results
                [{:result/adjudicator
                  {:adjudicator/number 3, :adjudicator/position 0},
                  :result/x-mark true}
                 {:result/adjudicator
                  {:adjudicator/number 4, :adjudicator/position 1},
                  :result/x-mark false}
                 {:result/adjudicator
                  {:adjudicator/number 5, :adjudicator/position 2},
                  :result/x-mark true}]}
               {:competitor/number 31,
                :competitor/recalled :r,
                :competitor/results
                [{:result/adjudicator
                  {:adjudicator/number 3, :adjudicator/position 0},
                  :result/x-mark false}
                 {:result/adjudicator
                  {:adjudicator/number 4, :adjudicator/position 1},
                  :result/x-mark true}
                 {:result/adjudicator
                  {:adjudicator/number 5, :adjudicator/position 2},
                  :result/x-mark false}]}
               {:competitor/number 32,
                :competitor/recalled :x,
                :competitor/results
                [{:result/adjudicator
                  {:adjudicator/number 3, :adjudicator/position 0},
                  :result/x-mark true}
                 {:result/adjudicator
                  {:adjudicator/number 4, :adjudicator/position 1},
                  :result/x-mark false}
                 {:result/adjudicator
                  {:adjudicator/number 5, :adjudicator/position 2},
                  :result/x-mark false}]}]
              }]}
           
           
           {:class/name "Hiphop Singel Star J Fl"
            :class/position 0
            :class/adjudicator-panel 0
            :class/dances
            []
            :class/competitors
            [{:competitor/name "Ringo Stingo"
              :competitor/club "Kapangg"
              :competitor/number 20}
             {:competitor/name "Greve Turbo"
              :competitor/club "OOoost"
              :competitor/number 21}]
            :class/results []}]}}))

(defn on-file-read [e file-reader]
  (let [result (.-result file-reader)]
    (log "On file read: send :file/import")
    (chsk-send! [:file/import {:content result}])))

(defn on-click-import-file [e]
  (log "Import clicked")
  (let [file (.item (.. e -target -files) 0)
        r (js/FileReader.)]
    (set! (.-onload r) #(on-file-read % r))
    (.readAsText r file)))

(defn dispatch [props]
  (let [id (first props)
        data (into [] (rest props))]
    (log (str "Dispatch of " id " with data " data))
    (match [id data]
           [:file/import [file]]
           (inc 1))))

(defn on-export-click [e competition]
  (log "Export clicked")
  (log (str competition))
  (chsk-send! [:file/export {:file/format :dance-perfect
                             :file/content competition}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; UI utils

(defn make-dance-type-presentation [dances]
  ;; Dances are presented as a list of the first letters of each dance
  (apply str (map #(first (:dance/name %)) dances)))

(defn- to-number [s]
  {:pre [(string? s)]}
  (let [prepared-string (clojure.string/replace s #" " "")]
    (cond (re-seq #"^[-+]?\d*[\.,]\d*$" prepared-string)
          (js/parseDouble (clojure.string/replace prepared-string #"," "."))
          (re-seq #"^[-+]?\d+$" prepared-string)
          (js/parseInt (clojure.string/replace prepared-string #"\+" ""))
          :else s)))

(defn number-string? [s]
  (if s
    (re-seq #"\d+" s)))

(defn make-round-presentation [round-status round-count]
  (str
   round-count " - "
   (if round-status
     (if (number-string? round-status)
       (str "Round " round-status)
       (condp = round-status
         "S" (str "Semifinal" )
         "E" "2nd Try"
         "F" "Final"
         "O" "Retry"
         (str "Unknown status : " round-status)))
     "Not Started")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Components

(defn dp-classes-component []
  [:table.table
   [:thead
    [:tr
     [:th {:with "20"} "#"]
     [:th {:with "200"} "Dansdisciplin"]
     [:th {:with "20"} "Panel"]
     [:th {:with "20"} "Typ"]
     [:th {:with "20"} "Startande"]
     [:th {:with "20"} "Status"]]]
   [:tbody
    (for [class (sort-by :class/position (:competition/classes (:competition @app-state)))]
      ^{:key class}
      [:tr
       [:td (inc (:class/position class))]
       [:td (:class/name class)]
       [:td (:class/adjudicator-panel class)]
       [:td (make-dance-type-presentation (:class/dances class))]
       
       [:td
        (let [results (:result/results (last (:class/results class)))
              started (count (:class/competitors class))
              recalled-count
              (if (empty? results)
                started
                (reduce
                 (fn [x y]
                   (if (contains?
                        #{:r :x}
                        (:competitor/recalled y))
                     (inc x)
                     x))
                 0
                 results))]
          (str recalled-count "/" started))]

       [:td (make-round-presentation (:result/round (last (:class/results class)))
                                     (count (:class/results class)))]])]])

;; TODO - make the on-click event run thoughe dispatch
(defn import-component []
  [:div
   [:h2 "Importera en ny tävling : "]
   [:input.btn.btn-primary.btn-lg {:type "file" :value "Import file"
                                   :onChange #(on-click-import-file %)}]])

(defn menu-component []
;  [:div]
  ;; (fn []
  ;;   (if (= (:selected-page @app-state) :import)))
  [:div
   [import-component]
   [:h3 "Klasser"]
   [dp-classes-component]])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Application

(defn ^:export run []
  (reagent/render-component [menu-component] (.-body js/document)))

;; (defn handle-export []
;;   (let [export-link (. js/document (getElementById "export-download-link"))]
;;     (log "Exporting competition")
;;     (.click export-link)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; Socket handling
(defn- event-handler [[id data :as ev] _]
  (log (str "Event: " id))
  (match [id data]
    ;; TODO Match your events here <...>
         [:chsk/recv [:file/imported content]]
         (do
           (swap! app-state #(merge % {:selected-page :classes
                                       :competition (:file/content content)}))
           ;(swap! app-state #(hash-map :competitions (conj (:competitions %) content)))
           (log (str @app-state))
           )
         ;; [:chsk/recv [:file/export content]]
         ;; (handle-export)
    [:chsk/state [:first-open _]] (log "Channel socket successfully established!")
    ;[:chsk/state new-state] (log (str "Chsk state change: " new-state))
    ;[:chsk/recv payload] (log (str "Push event from server: " payload))
    :else (log (str "Unmatched event: " ev)))
  )

(defonce chsk-router
  (sente/start-chsk-router-loop! event-handler ch-chsk))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Purgatory

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


;; TODO - Let the server return dancers in a UI-normalized way
;; (defn get-dancers [competitions]
;;   (into #{}
;;         (map #(dissoc % :competitor/number) 
;;              (for [competition competitions
;;                    classes (:competition/classes competition)
;;                    competitors (:class/competitors classes)]
;;                competitors))))

;; (defonce component-state
;;   (atom {:competitions-visible false}))

;; (defn competitor-component [competitors include-number?]
;;   [:div ""
;;    [:ul
;;     (for [competitor competitors]
;;       ^{:key competitor}
;;       [:li 
;;        (str (if include-number?
;;               (str "Tävlande nummer : " (:competitor/number competitor) " - "))
;;             (str (:competitor/name competitor)
;;                  " från "
;;                  (:competitor/club competitor)))])]])

;; (defn class-component [classes]
;;   [:h3 "Classes"]
;;   [:div
;;    (for [cls classes]
;;      ^{:key cls}
;;      [:div
;;       (:class/name cls)
;;       [competitor-component (:class/competitors cls) true]])])

;; (defn competition-item []
;;   (let [open (atom false)]
;;     (fn [competition]
;;       [:li 
;;        [:div.view
;;         [:label {:on-click #(log "Klickz")} (str (:competition/name competition) " i "
;;                       (:competition/location competition) " den "
;;                       (:competition/date competition))
;;          ]
;;         [:input.btn.btn-default
;;          {:type "button"
;;           :value (if @open "Stäng" "Öppna")
;;           :on-click #(swap! open not)}]]
;;        (if @open
;;          [:div
;;           [:input.btn.btn-default
;;            {:type "button"
;;             :value "Exportera"
;;             ;; TODO - send competition id when we got back-end storage
;;             :on-click #(on-export-click % competition)}]
;;           [:a {:id "export-download-link"
;;                :href (str "/exported-files/" (:competition/name competition) ".xml")
;;                :download (str (:competition/name competition) ".xml")}]
;;           [:h4 "Klasser :"]
;;           [class-component (:competition/classes competition)]])])))

;; (defn competitors-component []
;;   [:div
;;    [:h2 "Tillgängliga dansare"]
;;    [competitor-component (get-dancers (:competitions @app-state))]])

;; (defn import-component []
;;   [:div
   
;;    [:h2 "Tillgängliga tävlingar"]
;;    [:ul
;;     (for [competition (:competitions @app-state)]
;;       ^{:key competition} [competition-item competition])]
;;    [:h2 "Importera en ny tävling : "]
;;    [:input.btn.btn-default {:type "file" :value "Import file"
;;                             :onChange ;#(dispatch [:file/import (.item (.. % -target -files) 0)])
;;                             #(on-click-import-file %)
;;                             }]])

;; (defn menu-component []
;;   (let [visible-component (atom :none)]
;;     (fn []
;;       [:div.container
;;        [:div.header
;;         [:h2 "Välkommen till Tango!"]
;;         [:input.btn.btn-primary.btn-lg.navbar-btn
;;          {:type "button" :value "Tävlingar" :on-click #(reset! visible-component :competitions)}]
;;         [:input.btn.btn-primary.btn-lg
;;          {:type "button" :value "Dansare" :on-click #(reset! visible-component :competitors)}]
;;         [:input.btn.btn-primary.btn-lg
;;          {:type "button" :value "Domare" :on-click #(reset! visible-component :adjudicators)}]

;;         [:input.btn.btn-default
;;          {:type "button" :value "Debug Button" :on-click #(chsk-send! [:debug/test {:test "Test"}])}]
;;         ;[:h2 (str "visible-component " @visible-component)]
;;         ]
;;        (condp = @visible-component
;;          :competitions [import-component]
;;          :competitors [competitors-component]
;;          :adjudicators [:div]
;;          :none [:div])
;;        ])))
