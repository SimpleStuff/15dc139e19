(ns tango.export
  (:require [datascript.core :as d]
            [tango.test-utils :as u]
            [tango.ui-db :as uidb]
            [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]))

(defn export [data]
  (let [clean-data (uidb/sanitize data)
        db (d/create-conn uidb/schema)]
    (d/transact! db [clean-data])
    ))
(export u/expected-small-example)


