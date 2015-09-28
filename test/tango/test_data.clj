(ns tango.test-data
  (:require [clj-time.core :as tc]
            [clj-time.coerce :as tcr]))

(def small-file-expected-content
  {:competition/name "TurboMegatävling"
   :competition/date (tcr/to-date (tc/date-time 2014 11 22))
   :competition/location "THUNDERDOME"
   :dance-perfect/flags
   {:adj-order-final 1}
   :competition/classes
   [{:class/name "Hiphop Singel Star B"
     :class/position 0
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
       :competitor/number 30
       :competitor/position 0}
      {:competitor/name "Milan Lund"
       :competitor/club "Wilson"
       :competitor/number 31
       :competitor/position 1}
      {:competitor/name "Douglas Junger"
       :competitor/club "RGDT"
       :competitor/number 32
       :competitor/position 2}]
     :class/results []}

    {:class/name "Hiphop Singel Star J Fl"
     :class/position 1
     :class/adjudicator-panel 0
     :class/dances
     []
     :class/competitors
     [{:competitor/name "Ringo Stingo"
       :competitor/club "Kapangg"
       :competitor/number 20
       :competitor/position 0}
      {:competitor/name "Greve Turbo"
       :competitor/club "OOoost"
       :competitor/number 21
       :competitor/position 1}]
     :class/results []}]

   :competition/events
   []})

(def results-file-expected-content
  {:competition/name "TurboMegatävling"
   :competition/date (tcr/to-date (tc/date-time 2014 11 22))
   :competition/location "THUNDERDOME"
   :dance-perfect/flags {:adj-order-final 1}
   :competition/classes
   [{:class/name "Hiphop Singel Star B"
     :class/position 0
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
       :competitor/position 0
       :competitor/club "Sinclairs"
       :competitor/number 30}
      {:competitor/name "Milan Lund"
       :competitor/position 1
       :competitor/club "Wilson"
       :competitor/number 31}
      {:competitor/name "Douglas Junger"
       :competitor/position 2
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
         :competitor/recalled "",
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
         :competitor/recalled "",
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
     :class/position 1
     :class/adjudicator-panel 0
     :class/dances
     []
     :class/competitors
     [{:competitor/name "Ringo Stingo"
       :competitor/club "Kapangg"
       :competitor/number 20
       :competitor/position 0}
      {:competitor/name "Greve Turbo"
       :competitor/club "OOoost"
       :competitor/number 21
       :competitor/position 1}]
     :class/results []}]

   :competition/events
   []})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Events example

(def events-file-expected-content
  {:competition/name "TurboMegatävling"
   :competition/date (tcr/to-date (tc/date-time 2014 11 22))
   :competition/location "THUNDERDOME"
   :dance-perfect/flags
   {:adj-order-final 1}
   :competition/classes
   [{:class/name "Hiphop Singel Star B"
     :class/position 0
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
       :competitor/number 30
       :competitor/position 0}
      {:competitor/name "Milan Lund"
       :competitor/club "Wilson"
       :competitor/number 31
       :competitor/position 1}
      {:competitor/name "Douglas Junger"
       :competitor/club "RGDT"
       :competitor/number 32
       :competitor/position 2}]
     :class/results []}

    {:class/name "Hiphop Singel Star J Fl"
     :class/position 1
     :class/adjudicator-panel 0
     :class/dances
     []
     :class/competitors
     [{:competitor/name "Ringo Stingo"
       :competitor/club "Kapangg"
       :competitor/number 20
       :competitor/position 0}
      {:competitor/name "Greve Turbo"
       :competitor/club "OOoost"
       :competitor/number 21
       :competitor/position 1}]
     :class/results []}]

   :competition/events
   [{:event/position 0
     :event/class-number 0
     :event/number -1
     :event/time ""
     :event/comment ""
     :event/adjudicator-panel 0
     :event/heats 1
     :event/round :unknown-round-value
     :event/status 0
     :event/start-order 0}

    {:event/position 1
     :event/class-number 1
     :event/number 1
     :event/time "10:00"
     :event/comment "A comment"
     :event/adjudicator-panel 4
     :event/heats 2
     :event/round :normal-x
     :event/status 0
     :event/start-order 0}]})
