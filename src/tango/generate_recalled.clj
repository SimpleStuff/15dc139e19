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

"<html>
<head>
<title> Last Recalled </title>
</head>
<body>
<h1><font size=7>Event 4C</font></h1>
<h1><font size=7>Disco Par Guld B2</font></h1>
<h1><font size=7>Recalled from Semifinal</font></h1><br>
<table border=0 cellpadding=8 cellspacing=0>
</table>
<p><h1><font size=7>7 couples recalled</font></p>
<br><br><br><br>
<h1><font size=7>Event 4C</font></h1>
<h1><font size=7>Disco Par Guld B2</font></h1>
<h1><font size=6>Recalled from Semifinal</font></h1><br>
<h1><font size=7>
2090,  2092,  2094,  2095,  2097,  2098,  2100</font></h1><br><br><br><br><br><br>

</body>
</html>
"
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

"<html>
<head>
<title> Last Recalled </title>
</head>
<body>
<h1><font size=7>Event 4A</font></h1>
<h1><font size=7>Disco Par Guld B2</font>< /h1>
<h1><font size=7>Recalled from Semifinal</font>< /h1><br>
<table border=0 cellpadding=8 cellspacing=0>
<tr><td width=15%><h1><font size=7>2090</font>< /td><td width=65%><h1><font size=6>Miranda Nordheden - Thea Ergon</font>< /h1></td><td width=20%><h1><font size=6>Kindahls</font>< /h1></td>< /tr>
<tr><td width=15%><h1><font size=7>2092</font>< /td><td width=65%><h1><font size=6>Natalie Wallén - Tuja von Troil</font>< /h1></td><td width=20%><h1><font size=6>FLEX</font>< /h1></td>< /tr>
<tr><td width=15%><h1><font size=7>2094</font>< /td><td width=65%><h1><font size=6>Moa Norström - Nathalie Jireteg</font>< /h1></td><td width=20%><h1><font size=6>WAD/VDC< /font></h1>< /td></tr>
<tr><td width=15%><h1><font size=7>2095</font>< /td><td width=65%><h1><font size=6>Lovis Brask - Wilma Kritz</font>< /h1></td><td width=20%><h1><font size=6>RGDT</font>< /h1></td>< /tr>
<tr><td width=15%><h1><font size=7>2097</font>< /td><td width=65%><h1><font size=6>Elise Morling - Victoria Linder</font>< /h1></td><td width=20%><h1><font size=6>FLEX</font>< /h1></td>< /tr>
<tr><td width=15%><h1><font size=7>2098</font>< /td><td width=65%><h1><font size=6>Amanda Tidstål - Nowa Florinus</font>< /h1></td><td width=20%><h1><font size=6>WAD/VDC< /font></h1>< /td></tr>
<tr><td width=15%><h1><font size=7>2100</font>< /td><td width=65%><h1><font size=6>Clara Persson - Hanna Knutsson</font>< /h1></td><td width=20%><h1><font size=6>WAD/VDC< /font></h1>< /td></tr>
</table>
<p><h1><font size=7>7 couples recalled</font>< /p>
<br><br><br><br>
<h1><font size=7>Event 4A</font>< /h1>
<h1><font size=7>Disco Par Guld B2</font>< /h1>
<h1><font size=6>Recalled from Semifinal</font>< /h1><br>
<h1><font size=7>
2090,  2092,  2094,  2095,  2097,  2098,  2100</font>< /h1><br><br><br><br><br><br>

</body>
</html>"

"<html>
<head>
<title> Last Recalled </title>
</head>
<body>
<h1><font size=7>Event 4A</font>< / h1>
<h1><font size=7>Disco Par Guld B2</font>< / h1>
<h1><font size=7>Recalled from Semifinal</font>< / h1><br>
<table border=0 cellpadding=8 cellspacing=0>
<tr><td width=15%><h1><font size=7>2090</font>< / td><td width=65%><h1><font size=6>Miranda Nordheden - Thea Ergon</font>< / h1></td><td width=20%><h1><font size=6>Kindahls</font>< / h1></td>< / tr>
<tr><td width=15%><h1><font size=7>2092</font>< / td><td width=65%><h1><font size=6>Natalie Wallén - Tuja von Troil</font>< / h1></td><td width=20%><h1><font size=6>FLEX</font>< / h1></td>< / tr>
<tr><td width=15%><h1><font size=7>2094</font>< / td><td width=65%><h1><font size=6>Moa Norström - Nathalie Jireteg</font>< / h1></td><td width=20%><h1><font size=6>WAD/VDC< / font></h1>< / td></tr>
<tr><td width=15%><h1><font size=7>2095</font>< / td><td width=65%><h1><font size=6>Lovis Brask - Wilma Kritz</font>< / h1></td><td width=20%><h1><font size=6>RGDT</font>< / h1></td>< / tr>
<tr><td width=15%><h1><font size=7>2097</font>< / td><td width=65%><h1><font size=6>Elise Morling - Victoria Linder</font>< / h1></td><td width=20%><h1><font size=6>FLEX</font>< / h1></td>< / tr>
<tr><td width=15%><h1><font size=7>2098</font>< / td><td width=65%><h1><font size=6>Amanda Tidstål - Nowa Florinus</font>< / h1></td><td width=20%><h1><font size=6>WAD/VDC< / font></h1>< / td></tr>
<tr><td width=15%><h1><font size=7>2100</font>< / td><td width=65%><h1><font size=6>Clara Persson - Hanna Knutsson</font>< / h1></td><td width=20%><h1><font size=6>WAD/VDC< / font></h1>< / td></tr>
</table>
<p><h1><font size=7>7 couples recalled</font>< / p>
<br><br><br><br>
<h1><font size=7>Event 4A</font>< / h1>
<h1><font size=7>Disco Par Guld B2</font>< / h1>
<h1><font size=6>Recalled from Semifinal</font>< / h1><br>
<h1><font size=7>
2090,  2092,  2094,  2095,  2097,  2098,  2100</font>< / h1><br><br><br><br><br><br>

</body>
</html>"

(defn ppxml [xml]
  (let [in (javax.xml.transform.stream.StreamSource.
             (java.io.StringReader. xml))
        writer (java.io.StringWriter.)
        out (javax.xml.transform.stream.StreamResult. writer)
        transformer (.newTransformer
                      (javax.xml.transform.TransformerFactory/newInstance))]
    (.setOutputProperty transformer
                        javax.xml.transform.OutputKeys/INDENT "yes")
    (.setOutputProperty transformer
                        "{http://xml.apache.org/xslt}indent-amount" "2")
    (.setOutputProperty transformer
                        javax.xml.transform.OutputKeys/METHOD "xml")
    (.transform transformer in out)
    (-> out .getWriter .toString)))



;; API
(defn generate-recalled-html [competition]
  (let [completed (find-completed competition)
        recall-datas (mapv #(make-completed % competition) completed)]
    (mapv #(hash-map :activity/number (:activity/number %)
                     :html (generate-html %)
                     :html-re (generate-re-html %)
                     :text (generate-string %))
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
    (reduce #(do (write-fn (str "nv_re_" (:activity/number %2) ".htm") (:html %2))
                 (conj %1 (:activity/number %2)))
            exluded-rounds
            new-htmls)))
