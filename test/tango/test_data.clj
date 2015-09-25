(ns tango.test-data
  (:require [clj-time.core :as tc]
            [clj-time.coerce :as tcr]))

(def small-file-expected-content
  {:competition/name "TurboMegatävling"
   :competition/date (tcr/to-date (tc/date-time 2014 11 22))
   :competition/location "THUNDERDOME"
   :competition/classes
   [{:class/name "Hiphop Singel Star B"
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
       :competitor/number 32}]}
    {:class/name "Hiphop Singel Star J Fl"
     :class/adjudicator-panel 0
     :class/dances
     []
     :class/competitors
     [{:competitor/name "Ringo Stingo"
       :competitor/club "Kapangg"
       :competitor/number 20}
      {:competitor/name "Greve Turbo"
       :competitor/club "OOoost"
       :competitor/number 21}]}]})

(def results-file-expected-content
  {:competition/name "TurboMegatävling"
   :competition/date (tcr/to-date (tc/date-time 2014 11 22))
   :competition/location "THUNDERDOME"
   :competition/classes
   [{:class/name "Hiphop Singel Star B"
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
     :class/results []}]})
