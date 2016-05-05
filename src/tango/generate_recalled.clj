(ns tango.generate-recalled)

(defn generate-recalled [competition]
  (mapcat :class/rounds (:competition/classes competition)))
