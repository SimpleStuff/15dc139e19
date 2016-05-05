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
        temp-result {:activity/name  (:activity/name completed)
                     :activity/number (:activity/number completed)
                     :round/recalled (map :result/participant-number
                                          (filter #(not= (:result/recalled %) "")
                                                  (:round/results round)))
                     :round/presentation presentation
                     :round/index (:round/index round)}]
    temp-result))

(defn generate-html [recalled-info]
  (h/html [:head
           [:title "Last Recalled"]
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
           [:h1 [:font {:id "stor"} (clojure.string/join ", " (:round/recalled recalled-info))]]
           [:br]
           [:br]
           [:br]
           [:br]
           [:br]]))

;; API
(defn generate-recalled-html [competition]
  (let [completed (find-completed competition)
        recall-datas (mapv #(make-completed % competition) completed)]
    (mapv #(hash-map :activity/number (:activity/number %)
                     :html (generate-html %))
          recall-datas)))
