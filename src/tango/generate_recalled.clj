(ns tango.generate-recalled
  (:require [tango.presentation :as p]
            [hiccup.core :as h]))

;(defn generate-recalled [competition]
;  ;; TODO - koppla actvity name till class?
;  (let [rounds (mapcat :class/rounds (:competition/classes competition))
;        test-round "5A"
;        a-rounds-results (:round/results
;                           (first (filter (fn [x] (= (:round/number x) test-round))
;                                          (filter #(= (:round/status %) :completed) rounds))))]
;    (filter #(not= (:result/recalled %) "") a-rounds-results)))

(defn find-last-completed [competition]
  (let [non-comment-acts (filter #(not= (:activity/number %) -1)
                                 (:competition/activities competition))
        sorted (sort-by :activity/position non-comment-acts)
        all-completed (filter #(= :completed
                                  (:round/status (:activity/source %))) sorted)
        last-completed (last all-completed)
        round (:activity/source last-completed)
        class-for-round (first (filter #(= (:class/name %)
                                           (:activity/name last-completed))
                                       (:competition/classes competition)))
        presentation (p/make-time-schedule-activity-presenter last-completed
                                                              class-for-round)
        temp-result {:activity/name  (:activity/name last-completed)
                     :activity/number (:activity/number last-completed)
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
