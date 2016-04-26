(ns tango.export2
  (:require [clj-time.coerce :as tcr]
            [clj-time.format :as tf]
            [clj-time.core :as t]
            [clojure.data.xml :as xml]
            [clojure.xml :as cxml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            ))

(defn get-zip []
  (let [test-file
        (slurp (str "test/tango/examples/real-example-kungsor.xml"))]
    (zip/xml-zip
      (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes test-file))))))

(defn get-xml []
  (let [test-file
        (slurp (str "test/tango/examples/real-example-kungsor.xml"))]
    (clojure.xml/parse (java.io.ByteArrayInputStream. (.getBytes test-file)))))

;(def zipper (get-zip))
;
;(zx/attr (first (zx/xml-> zipper :CompData)) :Place)
;
;(zx/xml-> zipper :Class)
;
;(zx/attr (first (zx/xml-> zipper :ClassList :Class)) :Name)
;
;(zx/xml-> zipper :ClassList :Class)
;
;(first (zx/xml-> zipper :ClassList :Class))
;(xml/emit-str (xml/element :Result {:Seq 0}))
;; Producera ett <Result> i <Results>

;(def zipper (zip/xml-zip (get-xml)))
;
;(defn tree-edit
;  "Take a zipper, a function that matches a pattern in the tree,
;   and a function that edits the current location in the tree.  Examine the tree
;   nodes in depth-first order, determine whether the matcher matches, and if so
;   apply the editor."
;  [zipper matcher editor]
;  (loop [loc zipper]
;    (if (zip/end? loc)
;      (zip/root loc)
;      (if-let [matcher-result (matcher loc)]
;        (let [new-loc (zip/edit loc editor)]
;          (if (not (= (zip/node new-loc) (zip/node loc)))
;            (recur (zip/next new-loc))))
;        (recur (zip/next loc))))))
;
;;; match predicate, all book tags
;(defn match-book? [loc]
;  (let [tag (:tag (zip/node loc))]
;    ;; true if tag is of type <path>
;    (= :Class tag)))
;
;(def test-names
;  {"Hiphop Singel Guld U" "XXXXXXXXXXXXXXXXX"})
;
;;; edit function
;(defn editor [node]
;  (let [id (-> node :attrs :Name)
;        new-content (conj
;                      (:content node)
;                      (xml/element :price {} "XX"               ;(get test-names id id)
;                                   ))]
;    (assoc-in node [:content] new-content)))
;
;(def edited (tree-edit zipper match-book? editor))
;
;edited
;(xml/indent-str
;  edited)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def raw-xml (get-xml))

;; TODO - kolla om results finns pa icke startade
(let [content (get-in raw-xml [:content])
      class-list (nth content 2)
      class-to-fix (first (filter #(= "Hiphop Singel Brons B1" (:Name (:attrs %))) (:content class-list)))
      class-results (first (filter #(= :Results (:tag %)) (:content class-to-fix)))
      new-results (conj (:content class-results) {:tag     :Result
                                                  :attrs   {:hej "Marten"}
                                                  :content nil})]

  ;new-results
  (assoc class-results :content new-results)
  )

(def new-stuff
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Class) (if (= (:Name (:attrs form))
                                      "Hiphop Singel Brons B1")
                                 {:tag     :Class
                                  :attrs   {:Name "Changed Class"}
                                  :content nil}
                                 form)
        :else form))
    raw-xml))

(spit "export3.xml" (with-out-str (clojure.xml/emit new-stuff)))


(def raw-xml-2
  (xml/parse (java.io.FileInputStream. "test/tango/examples/real-example-kungsor.xml")))

(def new-stuff-2
  (clojure.walk/postwalk
    (fn [form]
      (cond
        (= (:tag form) :Class) (if (= (:Name (:attrs form))
                                      "Hiphop Singel Brons B1")
                                 (xml/element :Class {:Name "Changed Class"})
                                 form)
        :else form))
    raw-xml-2))

(spit "export4.xml" (xml/emit-str new-stuff-2))


;(defn sanitize [cmp]
;  (let [index (participant-index cmp)]
;    (clojure.walk/postwalk
;      (fn [form]
;        (cond
;          ;; replace participant-number with participant id in results
;          (:result/participant-number form) (dissoc
;                                              (assoc form :result/participant
;                                                          {:participant/id
;                                                           (get index
;                                                                (:result/participant-number form))})
;                                              :result/participant-number)
;
;          ;; adjudicator id should be in map form for lookup ref.
;          (:judging/adjudicator form) (assoc form :judging/adjudicator
;                                                  {:adjudicator/id (:judging/adjudicator form)})
;
;          ;; remove nil values
;          (map? form) (let [m (into {} (remove (comp nil? second) form))]
;                        (when (seq m)
;                          m))
;
;          :else form))
;      cmp)))