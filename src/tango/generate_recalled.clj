(ns tango.generate-recalled
  (:require [tango.presentation :as p]
            [hiccup.core :as h]))

(defn generate-recalled [competition]
  ;; TODO - koppla actvity name till class?
  (let [rounds (mapcat :class/rounds (:competition/classes competition))
        test-round "5A"
        a-rounds-results (:round/results
                           (first (filter (fn [x] (= (:round/number x) test-round))
                                          (filter #(= (:round/status %) :completed) rounds))))]
    (filter #(not= (:result/recalled %) "") a-rounds-results)))

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
                     :round/recalled (map :result/participant-number
                                          (filter #(not= (:result/recalled %) "")
                                                  (:round/results round)))
                     :round/type     (:round/type round)
                     ;:class          class-for-round
                     :round/presentation presentation
                     }]
    temp-result))

(defn generate-html [recalled-info]
  (h/html [:h1 (:activity/name recalled-info)]))
