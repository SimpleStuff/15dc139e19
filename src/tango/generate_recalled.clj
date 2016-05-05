(ns tango.generate-recalled
  (:require [tango.presentation :as p]
            [hiccup.core :as h]))

(defn find-completed [competition]
  (let [non-comment-acts (filter #(not= (:activity/number %) -1)
                                 (:competition/activities competition))
        sorted (sort-by :activity/position non-comment-acts)
        all-completed (filter #(= :completed
                                  (:round/status (:activity/source %))) sorted)]
    all-completed))

(defn make-completed [completed classes]
  (let [round (:activity/source completed)
        class-for-round (first (filter #(= (:class/name %)
                                           (:activity/name completed))
                                       classes))
        presentation (p/make-time-schedule-activity-presenter completed
                                                              class-for-round)
        temp-result {:activity/name   (:activity/name completed)
                     :activity/number (:activity/number completed)
                     :round/recalled (filter #(not= (:result/recalled %) "")
                                             (:round/results round))
                     :round/presentation presentation
                     :round/index (:round/index round)}]
    temp-result))

(defn generate-html [recalled-info]
  (h/html
    [:html
     [:head
      [:title " Last Recalled "]
      [:style {:type "text/css"} "#stor { Font-size: 54px; } #liten { Font-size: 36px; }"]]

     [:body
      [:h1 [:font {:id "stor"} (str "Event " (:activity/number recalled-info))]]
      [:h1 [:font {:id "stor"} (:activity/name recalled-info)]]
      [:h1 [:font {:id "liten"}
            (str "Recalled from "
                 (let [pres (:round
                              (:round/presentation recalled-info))]
                   (if (= pres "Normal")
                     (str "Round " (inc (:round/index recalled-info)))
                     pres)))]]
      [:h1 [:font {:id "stor"} (clojure.string/join ", "
                                                    (map :result/participant-number
                                                         (:round/recalled recalled-info)))]]
      [:br]
      [:br]
      [:br]
      [:br]
      [:br]]]))

(defn generate-re-html [recalled-info]
  (h/html
    [:html
     [:head
      [:title " Last Recalled "]
      ;[:style {:type "text/css"} "#stor { Font-size: 54px; } #liten { Font-size: 36px; }"]
      ]

     [:body
      [:h1 [:font {:size 7} (str "Event " (:activity/number recalled-info))]]
      [:h1 [:font {:size 7} (:activity/name recalled-info)]]
      [:h1 [:font {:size 7}
            (str "Recalled from "
                 (let [pres (:round
                              (:round/presentation recalled-info))]
                   (if (= pres "Normal")
                     (str "Round " (inc (:round/index recalled-info)))
                     pres)))]]
      [:table {:border 0 :cellpadding 8 :cellspacing 0}
       (for [recalled (:round/recalled recalled-info)]
         [:tr
          [:td {:width "15%"} [:h1 [:font {:size 7} (:result/participant-number recalled)]]]
          [:td {:width "65%"} [:h1 [:font {:size 6} "namn"  ;(:participant/name (:result/participant recalled))
                                    ]]]
          [:td {:width "20%"} [:h1 [:font {:size 6} "club"]]]])]

      [:p [:h1 [:font {:size 7} (str (count (:round/recalled recalled-info)) " couples recalled")]]]
      [:br] [:br] [:br] [:br]

      [:h1 [:font {:size 7} (str "Event " (:activity/number recalled-info))]]
      [:h1 [:font {:size 7} (:activity/name recalled-info)]]
      [:h1 [:font {:size 6}
            (str "Recalled from "
                 (let [pres (:round
                              (:round/presentation recalled-info))]
                   (if (= pres "Normal")
                     (str "Round " (inc (:round/index recalled-info)))
                     pres)))]] [:br]
      [:h1 [:font {:size 7} (clojure.string/join ", "
                                                 (map :result/participant-number
                                                      (:round/recalled recalled-info)))]]

      [:br]
      [:br]
      [:br]
      [:br]
      [:br]
      [:br]]]))

(defn generate-string [recalled-info]
  (let [pres (:round
               (:round/presentation recalled-info))
        from (if (= pres "Normal")
               (str "Round " (inc (:round/index recalled-info)))
               pres)
        parts (clojure.string/join ",  "
                                   (map :result/participant-number
                                        (:round/recalled recalled-info)))]
    (str "<html>
<head>
<title> Last Recalled </title>
</head>
<body>\n"
         "<h1><font size=7>" (:activity/number recalled-info) "</font></h1>\n"
         "<h1><font size=7>" (:activity/name recalled-info) "</font></h1>\n"
         "<h1><font size=7>Recalled from " from "</font></h1><br>\n"
         "<table border=0 cellpadding=8 cellspacing=0>\n"
         "</table>\n"
         "<p><h1><font size=7>" (str (count (:round/recalled recalled-info))) " couples recalled</font></p>\n"
         "<br><br><br><br>\n"
         "<h1><font size=7>Event " (:activity/number recalled-info) "</font></h1>\n"
         "<h1><font size=7>" (:activity/name recalled-info) "</font></h1>\n"
         "<h1><font size=6>Recalled from " from "</font></h1><br>\n"
         "<h1><font size=7>\n"
         parts "</font></h1><br><br><br><br><br><br>\n"
         "
</body>
</html>
")))

;; API
(defn generate-recalled-html [competition]
  (let [completed (find-completed competition)
        recall-datas (mapv #(make-completed % competition) completed)]
    (mapv #(hash-map :activity/number (:activity/number %)
                     :html (generate-html %)
                     ;:html-re (generate-re-html %)
                     ;:text (generate-string %)
                     )
          recall-datas)
    ;recall-datas
    ))

(defn write-recalled-html
  ;; Writes html files named 'ny_re_<round>.htm using data in the recalled-htmls and the write-fn
  ;; with the same api as spit.
  ;; collection, exluding any round included in exluded-rounds hash-set
  ;; Returns an updated hash-set to be supplied the next time this function is invoked.
  [exluded-rounds recalled-htmls write-fn]
  (let [new-htmls (filter #(nil? (get exluded-rounds (:activity/number %))) recalled-htmls)]
    (reduce #(do (write-fn (str "re_" (:activity/number %2) ".htm") (:html %2))
                 (conj %1 (:activity/number %2)))
            exluded-rounds
            new-htmls)))
