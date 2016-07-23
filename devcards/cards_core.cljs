 (ns devcards.cards-core
   (:require
     [devcards.core]
     [devcards.cards]
     [devcards.adjudicators]
     [tango.cljs.testing]))

 (defn ^:export main []
   (devcards.core/start-devcard-ui!))
