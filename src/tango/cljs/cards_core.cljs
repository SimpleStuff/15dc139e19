 (ns tango.cljs.cards-core
   (:require
     [devcards.core]
     [tango.cljs.cards]
     [tango.cljs.testing]))

 (defn ^:export main []
   (devcards.core/start-devcard-ui!))
