(ns tango.cljs.cards
  (:require-macros
    [devcards.core :as dc :refer [defcard deftest]]))

(enable-console-print!)

(defcard first-card
         "test")

(defcard om-component-card
         "render a om component")

(defn main []
  )
