 (ns devcards.cards-core
   (:require
     [devcards.core]
     [devcards.cards]
     [tango.cljs.testing]))

 (defn ^:export main []
   (devcards.core/start-devcard-ui!))
