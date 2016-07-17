(ns tango.cljs.runner
  (:require [doo.runner :refer-macros [doo-tests]]
            [tango.cljs.testing]))

(doo-tests 'tango.cljs.testing)