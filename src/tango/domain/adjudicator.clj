(ns tango.domain.adjudicator
  (:require [schema.core :as sch]
            [clojure.spec :as s]))

;http://stackoverflow.com/questions/37942495/where-to-put-specs-for-clojure-spec/37945794#37945794
;https://clojure.org/guides/spec
(s/def ::name string?)
(s/def ::country string?)
(s/def ::number string?)
(s/def ::id uuid?)

(s/def ::adjudicator (s/keys :req [::id ::name]
                             :opt [::country ::number]))

(s/explain ::adjudicator
           {::name "Apa"
            ::id (java.util.UUID/randomUUID)})

(s/conform ::adjudicator {::name "A"
                          ::id   (java.util.UUID/randomUUID)})

(defn create-adjudicator [id name]
  (let [raw {::id id ::name name}
        parsed (s/conform ::adjudicator raw)]
    (if (= parsed ::s/invalid)
      (throw (ex-info "Invalid input" (s/explain-data ::adjudicator raw)))
      parsed)))

(create-adjudicator (java.util.UUID/randomUUID) "Apa")

