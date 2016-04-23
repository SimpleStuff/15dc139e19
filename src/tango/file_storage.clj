(ns tango.file-storage
  (:require [clojure.edn :as edn]))

(defn save [competition path]
  (spit path (prn-str competition) :append false))

(defn read-file [path]
  (edn/read-string (slurp path)))
