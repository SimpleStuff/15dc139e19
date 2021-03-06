(ns tango.expected.expected-small-result)

(def expected-small-example
  {:competition/name "TurboMegatävling",
   :competition/date #inst "2014-11-22T00:00:00.000-00:00",
   :competition/location "THUNDERDOME",
   :competition/options
   {:dance-competition/same-heat-all-dances true,
    :presentation/chinese-fonts true,
    :dance-competition/heat-text-on-adjudicator-sheet true,
    :dance-competition/name-on-number-sign true,
    :dance-competition/skip-adjudicator-letter true,
    :presentation/courier-font "NSimSun",
    :dance-competition/adjudicator-order-final true,
    :dance-competition/random-order-in-heats true,
    :dance-competition/club-on-number-sign true,
    :dance-competition/adjudicator-order-other true,
    :presentation/arial-font "SimSun",
    :printer/preview true,
    :printer/printer-select-paper true}
   :competition/panels
   [{:adjudicator-panel/adjudicators [{:adjudicator/country "Sweden"
                                       :adjudicator/id      1
                                       :adjudicator/name    "Anders"
                                       :adjudicator/number  0}
                                      {:adjudicator/country ""
                                       :adjudicator/id      2
                                       :adjudicator/name    "Bertil"
                                       :adjudicator/number  1}
                                      {:adjudicator/country ""
                                       :adjudicator/id      3
                                       :adjudicator/name    "Cesar"
                                       :adjudicator/number  2}]
     :adjudicator-panel/id           4
     :adjudicator-panel/name         "All adjudicators"}
    {:adjudicator-panel/adjudicators [{:adjudicator/country "Sweden"
                                       :adjudicator/id      1
                                       :adjudicator/name    "Anders"
                                       :adjudicator/number  0}
                                      {:adjudicator/country ""
                                       :adjudicator/id      2
                                       :adjudicator/name    "Bertil"
                                       :adjudicator/number  1}]
     :adjudicator-panel/id           5
     :adjudicator-panel/name         "1"}
    {:adjudicator-panel/adjudicators [{:adjudicator/country ""
                                       :adjudicator/id      2
                                       :adjudicator/name    "Bertil"
                                       :adjudicator/number  1}
                                      {:adjudicator/country ""
                                       :adjudicator/id      3
                                       :adjudicator/name    "Cesar"
                                       :adjudicator/number  2}]
     :adjudicator-panel/id           6
     :adjudicator-panel/name         "2"}]
   :competition/adjudicators
   [{:adjudicator/country "Sweden"
     :adjudicator/id      1
     :adjudicator/name    "Anders"
     :adjudicator/number  0}
    {:adjudicator/country ""
     :adjudicator/id      2
     :adjudicator/name    "Bertil"
     :adjudicator/number  1}
    {:adjudicator/country ""
     :adjudicator/id      3
     :adjudicator/name    "Cesar"
     :adjudicator/number  2}]
   :competition/activities
   [{:activity/name "",
     :activity/number -1,
     :activity/comment "A comment",
     :activity/id 35,
     :activity/position 1,
     :activity/time nil,
     :activity/source nil}
    {:activity/name "Hiphop Singel Star B",
     :activity/number 1,
     :activity/comment "",
     :activity/id 37,
     :activity/position 2,
     :activity/time #inst "2014-11-22T10:00:00.000-00:00",
     :activity/source
     {:round/status :not-started,
      :round/panel
                    {:adjudicator-panel/name "2",
                     :adjudicator-panel/id 6,
                     :adjudicator-panel/adjudicators
                                             [{:adjudicator/id 2,
                                               :adjudicator/name "Bertil",
                                               :adjudicator/country ""}
                                              {:adjudicator/id 3,
                                               :adjudicator/name "Cesar",
                                               :adjudicator/country ""}]},
      :round/class-id 41,
      :round/id 36,
      :round/dances [{:dance/name "Medium"}],
      :round/results
      [{:result/participant-number 30,
        :result/recalled "",
        :result/judgings
                                   ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
                                     {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
                                     {:judging/adjudicator 3, :juding/marks [{:mark/x true}]})}
       {:result/participant-number 31,
        :result/recalled "",
        :result/judgings
                                   ({:judging/adjudicator 1, :juding/marks [{:mark/x false}]}
                                     {:judging/adjudicator 2, :juding/marks [{:mark/x true}]}
                                     {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}
       {:result/participant-number 32,
        :result/recalled "",
        :result/judgings
                                   ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
                                     {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
                                     {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}],
      :round/index 0,
      :round/starting
      [{:participant/name "Rulle Trulle",
        :participant/club "Sinus",
        :participant/number 30,
        :participant/id 38}
       {:participant/name "Hush Bush",
        :participant/club "Zilson",
        :participant/number 31,
        :participant/id 39}
       {:participant/name "Banana Hamock",
        :participant/club "Zzzz",
        :participant/number 32,
        :participant/id 40}],
      :round/heats 2,
      :round/type :normal-x,
      :round/number 1,
      :round/start-time #inst "2014-11-22T10:00:00.000-00:00",
      :round/recall 6}}]
   :competition/classes
                     [{:class/adjudicator-panel
                     {:adjudicator-panel/name "1",
                      :adjudicator-panel/id 5,
                      :adjudicator-panel/adjudicators
                                              [{:adjudicator/id 1,
                                                :adjudicator/name "Anders",
                                                :adjudicator/country "Sweden"}
                                               {:adjudicator/id 2,
                                                :adjudicator/name "Bertil",
                                                :adjudicator/country ""}]},
     :class/starting
                     [{:participant/name "Rulle Trulle",
                       :participant/club "Sinus",
                       :participant/number 30,
                       :participant/id 38}
                      {:participant/name "Hush Bush",
                       :participant/club "Zilson",
                       :participant/number 31,
                       :participant/id 39}
                      {:participant/name "Banana Hamock",
                       :participant/club "Zzzz",
                       :participant/number 32,
                       :participant/id 40}],
     :class/dances
                     [{:dance/name "Medium"}
                      {:dance/name "Tango"}
                      {:dance/name "VienWaltz"}],
     :class/remaining
                     [{:participant/name "Rulle Trulle",
                       :participant/club "Sinus",
                       :participant/number 30,
                       :participant/id 38}
                      {:participant/name "Hush Bush",
                       :participant/club "Zilson",
                       :participant/number 31,
                       :participant/id 39}
                      {:participant/name "Banana Hamock",
                       :participant/club "Zzzz",
                       :participant/number 32,
                       :participant/id 40}],
     :class/position 1,
     :class/name "Hiphop Singel Star B",
     :class/rounds
                     [{:round/status :not-started,
                       :round/panel
                                     {:adjudicator-panel/name "2",
                                      :adjudicator-panel/id 6,
                                      :adjudicator-panel/adjudicators
                                                              [{:adjudicator/id 2,
                                                                :adjudicator/name "Bertil",
                                                                :adjudicator/country ""}
                                                               {:adjudicator/id 3,
                                                                :adjudicator/name "Cesar",
                                                                :adjudicator/country ""}]},
                       :round/class-id 41,
                       :round/id 36,
                       :round/dances [{:dance/name "Medium"}],
                       :round/results
                       [{:result/participant-number 30,
                         :result/recalled "",
                         :result/judgings
                                                    ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
                                                      {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
                                                      {:judging/adjudicator 3, :juding/marks [{:mark/x true}]})}
                        {:result/participant-number 31,
                         :result/recalled "",
                         :result/judgings
                                                    ({:judging/adjudicator 1, :juding/marks [{:mark/x false}]}
                                                      {:judging/adjudicator 2, :juding/marks [{:mark/x true}]}
                                                      {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}
                        {:result/participant-number 32,
                         :result/recalled "",
                         :result/judgings
                                                    ({:judging/adjudicator 1, :juding/marks [{:mark/x true}]}
                                                      {:judging/adjudicator 2, :juding/marks [{:mark/x false}]}
                                                      {:judging/adjudicator 3, :juding/marks [{:mark/x false}]})}],
                       :round/index 0,
                       :round/starting
                       [{:participant/name "Rulle Trulle",
                         :participant/club "Sinus",
                         :participant/number 30,
                         :participant/id 38}
                        {:participant/name "Hush Bush",
                         :participant/club "Zilson",
                         :participant/number 31,
                         :participant/id 39}
                        {:participant/name "Banana Hamock",
                         :participant/club "Zzzz",
                         :participant/number 32,
                         :participant/id 40}],
                       :round/heats 2,
                       :round/type :normal-x,
                       :round/number 1,
                       :round/start-time #inst "2014-11-22T10:00:00.000-00:00",
                       :round/recall 6}],
     :class/id 41}
     {:class/adjudicator-panel nil,
      :class/starting
                               [{:participant/name "Ringo Stingo",
                                 :participant/club "Kapangg",
                                 :participant/number 20,
                                 :participant/id 42}
                                {:participant/name "Greve Turbo",
                                 :participant/club "OOoost",
                                 :participant/number 21,
                                 :participant/id 43}],
      :class/dances [],
      :class/remaining
                               [{:participant/name "Ringo Stingo",
                                 :participant/club "Kapangg",
                                 :participant/number 20,
                                 :participant/id 42}
                                {:participant/name "Greve Turbo",
                                 :participant/club "OOoost",
                                 :participant/number 21,
                                 :participant/id 43}],
      :class/position 2,
      :class/name "Hiphop Singel Star J Fl",
      :class/rounds [],
      :class/id 44}]
   }
  )

