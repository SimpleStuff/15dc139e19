(ns tango.file-resource
  (:require [clojure.edn :as edn]))

(defn save [competition path]
  (spit path (prn-str competition)))

(defn read-file [path]
  (edn/read-string (slurp path)))
